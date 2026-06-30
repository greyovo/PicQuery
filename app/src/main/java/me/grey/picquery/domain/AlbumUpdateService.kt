package me.grey.picquery.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.grey.picquery.common.encodeProgressCallback
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album
import timber.log.Timber

/**
 * 相册增量更新服务
 *
 * 负责编排相册的增量更新流程：
 * 1. 检测变更（新增照片、删除照片）
 * 2. 对新增照片进行编码并存储向量
 * 3. 删除已移除照片的向量
 * 4. 更新相册元数据（count、timestamp）
 *
 * 同时维护 ObjectBox（主向量存储）和 Room（相似度计算用）两个数据源的一致性。
 *
 * @param photoRepository 照片数据源（MediaStore 查询）
 * @param objectBoxEmbeddingRepository ObjectBox 向量仓库
 * @param embeddingRepository Room 向量仓库（相似度计算用）
 * @param albumRepository 相册元数据仓库
 * @param embeddingService 编码服务
 * @param dispatcher 协程调度器
 */
class AlbumUpdateService(
    private val photoRepository: PhotoRepository,
    private val objectBoxEmbeddingRepository: ObjectBoxEmbeddingRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val albumRepository: AlbumRepository,
    private val embeddingService: EmbeddingService,
    private val dispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "AlbumUpdateService"
    }

    /**
     * 检测指定相册的变更（不执行更新）
     *
     * @param album 已索引的相册
     * @return 变更描述
     */
    suspend fun detectChanges(album: Album): AlbumChange = withContext(dispatcher) {
        Timber.tag(TAG).d("Detecting changes for album: ${album.label} (id=${album.id})")

        // 获取当前 MediaStore 中的照片列表
        val currentPhotos = photoRepository.getPhotoListByAlbumId(album.id)

        // 获取已索引的照片 ID 集合（ObjectBox + Room 双查，以 ObjectBox 为准）
        val objectBoxIndexedIds = objectBoxEmbeddingRepository
            .getPhotoIdsByAlbumId(album.id)
            .toSet()

        // 同时检查 Room 中的索引 ID（用于诊断双存储不一致）
        val roomIndexedIds = embeddingRepository.getPhotoIdsByAlbumId(album.id).toSet()
        if (objectBoxIndexedIds != roomIndexedIds) {
            Timber.tag(TAG).w(
                "Storage inconsistency detected! ObjectBox: ${objectBoxIndexedIds.size}, " +
                    "Room: ${roomIndexedIds.size} for album ${album.label}"
            )
        }

        val change = AlbumChangeDetector.detect(album, currentPhotos, objectBoxIndexedIds)
        Timber.tag(TAG).d(
            "Changes for '${album.label}': +${change.addedCount} added, " +
                "-${change.removedCount} removed"
        )
        change
    }

    /**
     * 执行增量更新
     *
     * @param change 变更描述（由 [detectChanges] 产生）
     * @param progressCallback 进度回调
     * @return 更新结果
     */
    suspend fun applyUpdate(
        change: AlbumChange,
        progressCallback: encodeProgressCallback? = null
    ): AlbumUpdateResult = withContext(dispatcher) {
        if (!change.hasChanges) {
            Timber.tag(TAG).i("No changes to apply for album: ${change.album.label}")
            return@withContext AlbumUpdateResult.NoChange
        }

        Timber.tag(TAG).i(
            "Applying update for '${change.album.label}': " +
                "+${change.addedCount} to encode, -${change.removedCount} to remove"
        )

        var encodedCount = 0
        var removedCount = 0
        var encodeSuccess = true

        // 步骤 1：删除已移除照片的向量（先删除，避免残留）
        if (change.removedPhotoIds.isNotEmpty()) {
            val removedIdsArray = change.removedPhotoIds.toLongArray()
            try {
                objectBoxEmbeddingRepository.removeByPhotoIds(removedIdsArray)
                embeddingRepository.removeByPhotoIds(removedIdsArray)
                removedCount = change.removedPhotoIds.size
                Timber.tag(TAG).d("Removed $removedCount stale embeddings")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to remove stale embeddings")
            }
        }

        // 步骤 2：编码新增照片
        if (change.addedPhotos.isNotEmpty()) {
            encodeSuccess = embeddingService.encodePhotoList(
                photos = change.addedPhotos,
                progressCallback = progressCallback
            )
            if (encodeSuccess) {
                encodedCount = change.addedPhotos.size
                Timber.tag(TAG).d("Encoded $encodedCount new photos")
            } else {
                Timber.tag(TAG).w("Encoding was already in progress, new photos not encoded")
            }
        }

        // 步骤 3：更新相册元数据（count + timestamp，使 IndexMgrScreen 不再显示"需要更新"）
        if (encodeSuccess) {
            try {
                // 从 MediaStore 获取相册最新状态
                val currentAlbum = albumRepository.getAllAlbums()
                    .find { it.id == change.album.id }
                val updatedAlbum = if (currentAlbum != null) {
                    change.album.copy(
                        count = currentAlbum.count,
                        timestamp = currentAlbum.timestamp,
                        coverPath = currentAlbum.coverPath
                    )
                } else {
                    // 相册可能已被删除，用照片数量作为 count
                    change.album.copy(
                        count = photoRepository.getImageCountInAlbum(change.album.id).toLong()
                    )
                }
                albumRepository.addSearchableAlbum(updatedAlbum)
                Timber.tag(TAG).d("Updated album metadata: count=${updatedAlbum.count}")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to update album metadata")
            }
        }

        AlbumUpdateResult.Success(
            album = change.album,
            addedCount = encodedCount,
            removedCount = removedCount,
            allEncoded = encodeSuccess
        )
    }

    /**
     * 便捷方法：检测变更并立即执行更新
     *
     * @param album 已索引的相册
     * @param progressCallback 进度回调
     * @return 更新结果
     */
    suspend fun updateAlbum(
        album: Album,
        progressCallback: encodeProgressCallback? = null
    ): AlbumUpdateResult {
        val change = detectChanges(album)
        return applyUpdate(change, progressCallback)
    }
}

/**
 * 增量更新结果
 */
sealed class AlbumUpdateResult {
    /** 无变更 */
    data object NoChange : AlbumUpdateResult()

    /** 更新成功 */
    data class Success(
        val album: Album,
        val addedCount: Int,
        val removedCount: Int,
        val allEncoded: Boolean
    ) : AlbumUpdateResult()
}

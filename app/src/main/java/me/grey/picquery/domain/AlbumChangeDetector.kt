package me.grey.picquery.domain

import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo

/**
 * 相册变更检测器（纯逻辑，无 Android 依赖）
 *
 * 通过对比当前 MediaStore 中的照片 ID 集合与已索引的照片 ID 集合，
 * 计算出新增和删除的照片列表。
 *
 * 设计为纯函数式逻辑，便于单元测试：
 * - 输入：当前照片列表 + 已索引照片 ID 集合
 * - 输出：[AlbumChange] 变更描述
 *
 * 使用 HashSet 进行差集运算，时间复杂度 O(n)，适合大相册（数万张照片）。
 */
object AlbumChangeDetector {

    /**
     * 纯 ID 差分（无任何外部依赖，完全可测试）
     *
     * @param currentPhotoIds 当前照片 ID 集合
     * @param indexedPhotoIds 已索引照片 ID 集合
     * @return ID 差分结果
     */
    fun diffIds(
        currentPhotoIds: Set<Long>,
        indexedPhotoIds: Set<Long>
    ): IdDiff {
        val addedIds = currentPhotoIds - indexedPhotoIds
        val removedIds = indexedPhotoIds - currentPhotoIds
        return IdDiff(addedIds, removedIds)
    }

    /**
     * 检测相册变更
     *
     * @param album 目标相册
     * @param currentPhotos 当前 MediaStore 中该相册的所有照片
     * @param indexedPhotoIds 已在向量数据库中索引的照片 ID 集合
     * @return 变更描述 [AlbumChange]
     */
    fun detect(
        album: Album,
        currentPhotos: List<Photo>,
        indexedPhotoIds: Set<Long>
    ): AlbumChange {
        val currentPhotoIds = HashSet<Long>(currentPhotos.size)
        for (photo in currentPhotos) {
            currentPhotoIds.add(photo.id)
        }

        val diff = diffIds(currentPhotoIds, indexedPhotoIds)

        // 新增照片：当前存在但未索引
        val addedPhotos = currentPhotos.filter { it.id in diff.addedIds }

        return AlbumChange(
            album = album,
            addedPhotos = addedPhotos,
            removedPhotoIds = diff.removedIds
        )
    }

    /**
     * 批量检测多个相册的变更
     *
     * @param albums 目标相册列表
     * @param currentPhotosByAlbumId 每个相册当前的照片列表（key 为 albumId）
     * @param indexedPhotoIdsByAlbumId 每个相册已索引的照片 ID 集合（key 为 albumId）
     * @return 每个相册的变更描述列表
     */
    fun detectBatch(
        albums: List<Album>,
        currentPhotosByAlbumId: Map<Long, List<Photo>>,
        indexedPhotoIdsByAlbumId: Map<Long, Set<Long>>
    ): List<AlbumChange> {
        return albums.map { album ->
            detect(
                album = album,
                currentPhotos = currentPhotosByAlbumId[album.id] ?: emptyList(),
                indexedPhotoIds = indexedPhotoIdsByAlbumId[album.id] ?: emptySet()
            )
        }
    }
}

/**
 * ID 差分结果（纯数据，无 Android 依赖）
 */
data class IdDiff(
    val addedIds: Set<Long>,
    val removedIds: Set<Long>
) {
    val hasChanges: Boolean get() = addedIds.isNotEmpty() || removedIds.isNotEmpty()
    val addedCount: Int get() = addedIds.size
    val removedCount: Int get() = removedIds.size
}

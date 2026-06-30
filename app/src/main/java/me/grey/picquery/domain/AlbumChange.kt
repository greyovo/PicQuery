package me.grey.picquery.domain

import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo

/**
 * 相册变更检测结果
 *
 * 表示一个已索引相册相对于当前 MediaStore 状态的变更：
 * - [addedPhotos]: 新增的照片（需要编码并添加向量）
 * - [removedPhotoIds]: 已删除的照片 ID（需要移除对应向量）
 * - [modifiedPhotos]: 内容已修改的照片（预留字段，v1 暂不实现检测逻辑）
 *
 * @property album 发生变更的相册
 * @property addedPhotos 当前存在但尚未索引的照片列表
 * @property removedPhotoIds 已索引但当前已从相册中删除的照片 ID
 * @property modifiedPhotos 已索引且当前仍存在但内容可能已变更的照片（预留）
 */
data class AlbumChange(
    val album: Album,
    val addedPhotos: List<Photo>,
    val removedPhotoIds: Set<Long>,
    val modifiedPhotos: List<Photo> = emptyList()
) {
    /** 是否存在任何变更 */
    val hasChanges: Boolean
        get() = addedPhotos.isNotEmpty() || removedPhotoIds.isNotEmpty() || modifiedPhotos.isNotEmpty()

    /** 新增照片数量 */
    val addedCount: Int get() = addedPhotos.size

    /** 删除照片数量 */
    val removedCount: Int get() = removedPhotoIds.size

    /** 变更总数量（用于 UI 显示） */
    val totalChangeCount: Int
        get() = addedCount + removedCount + modifiedPhotos.size
}

package me.grey.picquery.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class Photo(
    val id: Long,
    val label: String,
    val uri: Uri,
    val path: String,
    val timestamp: Long, // 最后修改的日期，时间戳
    val albumID: Long,
    val albumLabel: String
) {
    override fun toString(): String {
        return "Photo(id=$id, label='$label', uri=$uri, path='$path', timestamp=$timestamp, albumID=$albumID, albumLabel=$albumLabel)"
    }

    fun toPhotoResult(score: Float): PhotoResult {
        return PhotoResult(id, label, uri, path, timestamp, albumID, albumLabel, score)
    }
}

@Serializable
data class PhotoItem(
    val id: Long,
    val label: String,
    val uri: String,
    val path: String,
    val timestamp: Long,
    val albumID: Long,
    val albumLabel: String
) {
    override fun toString(): String {
        return "Photo(id=$id, label='$label', uri=$uri, path='$path', timestamp=$timestamp, albumID=$albumID, albumLabel=$albumLabel)"
    }
}

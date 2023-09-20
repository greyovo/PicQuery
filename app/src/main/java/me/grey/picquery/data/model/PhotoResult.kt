package me.grey.picquery.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class PhotoResult(
    /**
     * Copy from [Photo]
     */
    val id: Long,
    val label: String,
    val uri: Uri,
    val path: String,
    val timestamp: Long,
    val albumID: Long,
    val albumLabel: String,

    // Similarity Score, for Ranking
    val score: Float,
) {
    override fun toString(): String {
        return "PhotoResult(score=$score, id=$id, label='$label', albumLabel=$albumLabel path=$path)"
    }
}


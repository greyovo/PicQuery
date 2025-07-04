package me.grey.picquery.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType
import java.io.Serializable

@Entity
data class ObjectBoxEmbedding(
    @Id
    var id: Long = 0,

    @Index
    val photoId: Long,

    @Index
    val albumId: Long,

    @HnswIndex(
        dimensions = 512,
        distanceType = VectorDistanceType.COSINE
    )
    val data: FloatArray // Vector data as FloatArray
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ObjectBoxEmbedding

        if (photoId != other.photoId) return false
        if (albumId != other.albumId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = photoId.hashCode()
        result = 31 * result + albumId.hashCode()
        return result
    }

    companion object {
        const val PHOTO_ID = "photoId"
        const val ALBUM_ID = "albumId"
    }
}

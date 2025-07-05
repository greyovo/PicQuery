package me.grey.picquery.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity
data class Embedding(
    @PrimaryKey
    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "album_id")
    val albumId: Long,

    @ColumnInfo(name = "data", typeAffinity = ColumnInfo.BLOB)
    val data: ByteArray // Actually a `FloatArray`. Need to be converted before using.
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Embedding

        if (photoId != other.photoId) return false
        if (albumId != other.albumId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = photoId.hashCode()
        result = 31 * result + albumId.hashCode()
        return result
    }
}

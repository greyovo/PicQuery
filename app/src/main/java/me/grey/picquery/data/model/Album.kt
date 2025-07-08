package me.grey.picquery.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "searchable_album")
data class Album(
    @PrimaryKey
    val id: Long = 0,
    val label: String,
    var coverPath: String,
    var timestamp: Long,
    var count: Long = 0
) {
    override fun toString(): String {
        return "Album(id=$id, label='$label', path='$coverPath', timestamp=$timestamp, count=$count)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Album

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

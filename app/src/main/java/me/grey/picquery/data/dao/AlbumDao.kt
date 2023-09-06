package me.grey.picquery.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import me.grey.picquery.data.model.Album

@Dao
interface AlbumDao {
    companion object {
        private const val tableName = "searchable_album"
    }

    @Query("SELECT * FROM $tableName")
    fun getAll(): List<Album>

    @Query(
        "SELECT * FROM $tableName WHERE id= (:id)"
    )
    fun getById(id: Long): Album

    @Upsert
    fun upsertAll(albums: List<Album>)

    @Delete
    fun delete(album: Album)

    @Delete
    fun deleteAll(albums: List<Album>)
}
package me.grey.picquery.data.dao

import androidx.room.*
import me.grey.picquery.data.model.Embedding

@Dao
interface EmbeddingDao {
    companion object {
        private const val tableName = "embedding"
    }

    @Query("SELECT * FROM $tableName")
    fun getAll(): List<Embedding>

    @Query("SELECT COUNT(*) FROM $tableName")
    fun getTotalCount(): Long

    @Query("SELECT * FROM $tableName WHERE photo_id IN (:photoIds)")
    fun getAllByPhotoIds(photoIds: LongArray): List<Embedding>

    @Query(
        "SELECT * FROM $tableName WHERE album_id IS (:albumId)"
    )
    fun getAllByAlbumId(albumId: Long): List<Embedding>

//    @Insert
//    fun insertAll(embeddings: List<Embedding>)

    @Upsert
    fun upsertAll(embeddings: List<Embedding>)

    @Delete
    fun delete(embedding: Embedding)

    @Delete
    fun deleteAll(embeddings: List<Embedding>)
}
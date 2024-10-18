package me.grey.picquery.data.data_source

import me.grey.picquery.data.AppDatabase
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Embedding

class EmbeddingRepository(
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "EmbeddingRepo"
    }

    fun getAll(): List<Embedding> {
        return database.embeddingDao().getAll()
    }

//    fun hasEmbedding(): Boolean {
//        val db = AppDatabase.instance
//        val total = db.embeddingDao().getTotalCount()
//        return total > 0
//    }

    fun getTotalCount(): Long {
        return database.embeddingDao().getTotalCount()
    }

    fun getByAlbumId(albumId: Long): List<Embedding> {
        return database.embeddingDao().getAllByAlbumId(albumId)
    }

    fun getByAlbumList(albumList: List<Album>): List<Embedding> {
        return database.embeddingDao().getByAlbumIdList(albumList.map { it.id })
    }

    fun update(emb: Embedding) {
        return database.embeddingDao().upsertAll(listOf(emb))
    }

    fun updateAll(list: List<Embedding>) {
        return database.embeddingDao().upsertAll(list)
    }
}
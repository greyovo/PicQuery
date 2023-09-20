package me.grey.picquery.data.data_source

import me.grey.picquery.data.AppDatabase
import me.grey.picquery.data.model.Embedding

class EmbeddingRepository {
    companion object {
        private const val TAG = "EmbeddingRepo"
    }

    fun getAll(): List<Embedding> {
        val db = AppDatabase.instance
        return db.embeddingDao().getAll()
    }

//    fun hasEmbedding(): Boolean {
//        val db = AppDatabase.instance
//        val total = db.embeddingDao().getTotalCount()
//        return total > 0
//    }

    fun getTotalCount(): Long {
        val db = AppDatabase.instance
        return db.embeddingDao().getTotalCount()
    }

    fun getByAlbumId(albumId: Long): List<Embedding> {
        val db = AppDatabase.instance
        return db.embeddingDao().getAllByAlbumId(albumId)
    }

    fun update(emb: Embedding) {
    }

    fun updateAll(list: List<Embedding>) {
        val db = AppDatabase.instance
        return db.embeddingDao().upsertAll(list)
    }
}
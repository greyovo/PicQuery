package me.grey.picquery.data

import me.grey.picquery.data.model.Embedding

class EmbeddingRepository {
    companion object {
        private const val TAG = "EmbeddingRepo"
    }

    fun getAll(): List<Embedding> {
        val db = AppDatabase.instance
        return db.embeddingDao().getAll()
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
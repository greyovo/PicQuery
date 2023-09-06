package me.grey.picquery.data

import android.util.Log
import me.grey.picquery.data.model.Embedding
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class ImageEmbeddingRepository {
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
package me.grey.picquery.data.data_source

import me.grey.picquery.data.AppDatabase
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Embedding
import java.util.concurrent.LinkedBlockingDeque

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

    fun updateList(e: Embedding) {
        cacheLinkedBlockingDeque.add(e)
        if (cacheLinkedBlockingDeque.size >= 300) {
            val toUpdate = cacheLinkedBlockingDeque.toList()
            cacheLinkedBlockingDeque.clear()
            return database.embeddingDao().upsertAll(toUpdate)
        }
    }

    fun updateCache() {
        if (cacheLinkedBlockingDeque.isNotEmpty()) {
            val toUpdate = cacheLinkedBlockingDeque.toList()
            cacheLinkedBlockingDeque.clear()
            return database.embeddingDao().upsertAll(toUpdate)
        }
    }

    private val cacheLinkedBlockingDeque = LinkedBlockingDeque<Embedding>()
    fun updateAll(list: List<Embedding>) {
        return database.embeddingDao().upsertAll(list)
    }

    fun removeByAlbum(album: Album){
        return database.embeddingDao().removeByAlbumId(album.id)
    }
}
package me.grey.picquery.data.data_source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
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

    fun getAllEmbeddingsPaginated(batchSize: Int): Flow<List<Embedding>> = flow {
        var offset = 0
        while (true) {
            val embeddings = database.embeddingDao().getEmbeddingsPaginated(batchSize, offset)
            if (embeddings.isEmpty()) {
                emit(emptyList())
                break
            }
            emit(embeddings)
            offset += batchSize
        }
    }

    fun getTotalCount(): Long {
        return database.embeddingDao().getTotalCount()
    }

    fun getByAlbumId(albumId: Long): List<Embedding> {
        return database.embeddingDao().getAllByAlbumId(albumId)
    }

    fun getByAlbumList(albumList: List<Album>): List<Embedding> {
        return database.embeddingDao().getByAlbumIdList(albumList.map { it.id })
    }

    fun getEmbeddingsByAlbumIdsPaginated(albumIds: List<Long>, batchSize: Int): Flow<List<Embedding>> = flow {
        var offset = 0
        while (true) {
            val embeddings = database.embeddingDao().getByAlbumIdList(albumIds, batchSize, offset)
            if (embeddings.isEmpty()) {
                emit(emptyList())
                break
            }
            emit(embeddings)
            offset += batchSize
        }
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
}
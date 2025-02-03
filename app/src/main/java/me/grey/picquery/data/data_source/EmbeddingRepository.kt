package me.grey.picquery.data.data_source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.grey.picquery.data.dao.EmbeddingDao
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Embedding
import java.util.concurrent.LinkedBlockingDeque

class EmbeddingRepository(
    private val dataSource: EmbeddingDao
) {
    companion object {
        private const val TAG = "EmbeddingRepo"
    }

    fun getAll(): List<Embedding> {
        return dataSource.getAll()
    }

    fun getAllEmbeddingsPaginated(batchSize: Int): Flow<List<Embedding>> = flow {
        var offset = 0
        while (true) {
            val embeddings = dataSource.getEmbeddingsPaginated(batchSize, offset)
            if (embeddings.isEmpty()) {
                emit(emptyList())
                break
            }
            emit(embeddings)
            offset += batchSize
        }
    }

    fun getTotalCount(): Long {
        return dataSource.getTotalCount()
    }

    fun getByAlbumId(albumId: Long): List<Embedding> {
        return dataSource.getAllByAlbumId(albumId)
    }

    fun getByAlbumList(albumList: List<Album>): List<Embedding> {
        return dataSource.getByAlbumIdList(albumList.map { it.id })
    }

    fun getEmbeddingsByAlbumIdsPaginated(albumIds: List<Long>, batchSize: Int): Flow<List<Embedding>> = flow {
        var offset = 0
        while (true) {
            val embeddings = dataSource.getByAlbumIdList(albumIds, batchSize, offset)
            if (embeddings.isEmpty()) {
                emit(emptyList())
                break
            }
            emit(embeddings)
            offset += batchSize
        }
    }

    fun update(emb: Embedding) {
        return dataSource.upsertAll(listOf(emb))
    }

    fun updateList(e: Embedding) {
        cacheLinkedBlockingDeque.add(e)
        if (cacheLinkedBlockingDeque.size >= 300) {
            val toUpdate = cacheLinkedBlockingDeque.toList()
            cacheLinkedBlockingDeque.clear()
            return dataSource.upsertAll(toUpdate)
        }
    }

    fun updateCache() {
        if (cacheLinkedBlockingDeque.isNotEmpty()) {
            val toUpdate = cacheLinkedBlockingDeque.toList()
            cacheLinkedBlockingDeque.clear()
            return dataSource.upsertAll(toUpdate)
        }
    }

    private val cacheLinkedBlockingDeque = LinkedBlockingDeque<Embedding>()
    fun updateAll(list: List<Embedding>) {
        return dataSource.upsertAll(list)
    }
}
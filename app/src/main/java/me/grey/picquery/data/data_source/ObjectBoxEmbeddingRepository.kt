package me.grey.picquery.data.data_source

import io.objectbox.query.ObjectWithScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.grey.picquery.data.dao.ObjectBoxEmbeddingDao
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.ObjectBoxEmbedding
import me.grey.picquery.data.model.toFloatArray
import java.util.concurrent.LinkedBlockingDeque

class ObjectBoxEmbeddingRepository(
    private val dataSource: ObjectBoxEmbeddingDao
) {
    companion object {
        private const val TAG = "ObjectBoxEmbeddingRepo"
    }

    fun getAll(): List<ObjectBoxEmbedding> {
        return dataSource.getAll()
    }

    suspend fun getEmbeddingByPhotoId(photoId: Long): ObjectBoxEmbedding? {
        return withContext(Dispatchers.IO) {
            dataSource.getEmbeddingByPhotoId(photoId)
        }
    }

    fun getAllEmbeddingsPaginated(pageSize: Int): Flow<List<ObjectBoxEmbedding>> = flow {
        var offset = 0
        var hasMore = true

        while (hasMore) {
            val embeddings = dataSource.getEmbeddingsPaginated(pageSize, offset)
            if (embeddings.isEmpty()) {
                hasMore = false
            } else {
                emit(embeddings)
                offset += pageSize
            }
        }
    }

    fun getTotalCount(): Long {
        return dataSource.getTotalCount()
    }

    fun getByPhotoIds(photoIds: LongArray): List<ObjectBoxEmbedding> {
        return dataSource.getAllByPhotoIds(photoIds)
    }

    fun getByAlbumId(albumId: Long): List<ObjectBoxEmbedding> {
        return dataSource.getAllByAlbumId(albumId)
    }

    fun getByAlbumList(albumList: List<Album>): List<ObjectBoxEmbedding> {
        return dataSource.getByAlbumIdList(albumList.map { it.id })
    }

    fun getEmbeddingsByAlbumIdsPaginated(albumIds: List<Long>, batchSize: Int): Flow<List<ObjectBoxEmbedding>> = flow {
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

    fun update(emb: ObjectBoxEmbedding) {
        return dataSource.upsertAll(listOf(emb))
    }

    private val cacheLinkedBlockingDeque = LinkedBlockingDeque<ObjectBoxEmbedding>()

    fun updateList(e: ObjectBoxEmbedding) {
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

    fun updateAll(list: List<ObjectBoxEmbedding>) {
        return dataSource.upsertAll(list)
    }

    fun removeByAlbum(album: Album) {
        return dataSource.removeByAlbumId(album.id)
    }

    fun searchByVector(vector: ByteArray):List<ObjectWithScore<ObjectBoxEmbedding>> {
        return dataSource.searchNearestVectors(vector.toFloatArray())
    }


    fun searchNearestVectors(
        queryVector: FloatArray,
        topK: Int = 10,
        similarityThreshold: Float = 0.7f,
        albumIds: List<Long>? = null
    ): List<ObjectWithScore<ObjectBoxEmbedding>> {
        return dataSource.searchNearestVectors(
            queryVector,
            topK,
            similarityThreshold,
            albumIds
        )
    }

    fun findSimilarEmbeddings(
        queryVector: FloatArray,
        topK: Int = 30,
        similarityThreshold: Float = 0.95f,
        albumIds: List<Long>? = null
    ): List<ObjectWithScore<ObjectBoxEmbedding>> {
        return dataSource.searchNearestVectors2(
            queryVector,
            topK,
            similarityThreshold,
            albumIds
        )
    }
}
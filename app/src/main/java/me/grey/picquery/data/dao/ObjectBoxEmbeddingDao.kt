package me.grey.picquery.data.dao

import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.ObjectWithScore
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.data.model.ObjectBoxEmbedding
import me.grey.picquery.data.model.ObjectBoxEmbedding_
import timber.log.Timber

class ObjectBoxEmbeddingDao(private val embeddingBox: Box<ObjectBoxEmbedding>) {
    fun getAll(): List<ObjectBoxEmbedding> {
        return embeddingBox.all
    }

    // 分页查询所有嵌入向量
    fun getEmbeddingsPaginated(limit: Int, offset: Int): List<ObjectBoxEmbedding> {
        return embeddingBox.query {
            orderDesc(ObjectBoxEmbedding_.photoId)
        }.find(offset.toLong(), limit.toLong())
    }

    // 按相册ID分页查询嵌入向量
    fun getEmbeddingsByAlbumIdPaginated(albumId: Long, limit: Int, offset: Int): List<ObjectBoxEmbedding> {
        return embeddingBox.query {
            equal(ObjectBoxEmbedding_.albumId, albumId)
            orderDesc(ObjectBoxEmbedding_.photoId)
        }.find(offset.toLong(), limit.toLong())
    }

    // 按多个相册ID分页查询嵌入向量
    fun getEmbeddingsByAlbumIdsPaginated(albumIds: List<Long>, limit: Int, offset: Int): List<ObjectBoxEmbedding> {
        return embeddingBox.query {
            `in`(ObjectBoxEmbedding_.albumId, albumIds.toLongArray())
            orderDesc(ObjectBoxEmbedding_.photoId)
        }.find(offset.toLong(), limit.toLong())
    }

    // 获取分页查询的总数
    fun getEmbeddingsCountByAlbumIds(albumIds: List<Long>): Long {
        return embeddingBox.query {
            `in`(ObjectBoxEmbedding_.albumId, albumIds.toLongArray())
            orderDesc(ObjectBoxEmbedding_.photoId)
        }.count()
    }

    fun getEmbeddingByPhotoId(photoId: Long): ObjectBoxEmbedding? {
        return embeddingBox
            .query { equal(ObjectBoxEmbedding_.photoId, photoId) }
            .findFirst()
    }

    fun getAllByPhotoIds(photoIds: LongArray): List<ObjectBoxEmbedding> {
        return embeddingBox.query {
            `in`(ObjectBoxEmbedding_.photoId, photoIds)
            orderDesc(ObjectBoxEmbedding_.photoId)
        }.find()
    }

    // 获取总数
    fun getTotalCount(): Long {
        return embeddingBox.count()
    }

    // 根据相册ID获取嵌入向量（精确匹配）
    fun getAllByAlbumId(albumId: Long): List<ObjectBoxEmbedding> {
        return embeddingBox.query {
            equal(ObjectBoxEmbedding_.albumId, albumId)
            orderDesc(ObjectBoxEmbedding_.photoId)
        }.find()
    }

    // 根据相册ID列表获取嵌入向量
    fun getByAlbumIdList(albumIds: List<Long>): List<ObjectBoxEmbedding> {
        return embeddingBox.query {
            `in`(ObjectBoxEmbedding_.albumId, albumIds.toLongArray())
            orderDesc(ObjectBoxEmbedding_.photoId)
        }.find()
    }

    // 根据相册ID列表分页获取嵌入向量
    fun getByAlbumIdList(albumIds: List<Long>, limit: Int, offset: Int): List<ObjectBoxEmbedding> {
        return embeddingBox.query {
            `in`(ObjectBoxEmbedding_.albumId, albumIds.toLongArray())
            orderDesc(ObjectBoxEmbedding_.photoId)
        }.find(offset.toLong(), limit.toLong())
    }

    // 根据指定相册ID删除嵌入向量
    fun removeByAlbumId(albumId: Long) {
        embeddingBox.query {
            equal(ObjectBoxEmbedding_.albumId, albumId)
        }.remove()
    }

    // 批量更新或插入嵌入向量
    fun upsertAll(embeddings: List<ObjectBoxEmbedding>) {
        embeddingBox.put(embeddings)
    }

    // 删除单个嵌入向量
    fun delete(embedding: ObjectBoxEmbedding) {
        embeddingBox.remove(embedding)
    }

    // 批量删除嵌入向量
    fun deleteAll(embeddings: List<ObjectBoxEmbedding>) {
        embeddingBox.remove(embeddings)
    }

    fun searchNearestVectors(
        queryVector: FloatArray,
        topK: Int = 10,
        similarityThreshold: Float = 0.7f,
        albumIds: List<Long>? = null
    ): List<ObjectWithScore<ObjectBoxEmbedding>> {
        val query =
            embeddingBox
                .query()
                .nearestNeighbors(ObjectBoxEmbedding_.data, queryVector, topK)
                .build()

        val results = query.findWithScores().filter { result ->
            val cosineSimilarity = 1.0 - result.score
            cosineSimilarity > similarityThreshold
        }

        results.forEachIndexed { index, result ->
            Timber.d("Result $index:")
            Timber.d("Photo ID: ${result.get().photoId}")
            Timber.d("Score: ${result.score}")
            Timber.d("Cosine Similarity: ${calculateSimilarity(queryVector, result.get().data)}")
        }

        return results
    }

    fun searchNearestVectors2(
        queryVector: FloatArray,
        topK: Int = 10,
        similarityThreshold: Float = 0.95f,
        albumIds: List<Long>? = null
    ): List<ObjectWithScore<ObjectBoxEmbedding>> {
        val query =
            embeddingBox
                .query()
                .nearestNeighbors(ObjectBoxEmbedding_.data, queryVector, topK)
                .build()

        val results = query.findWithScores()
            .filter { result ->

                val cosineSimilarity = 1.0 - result.score

                Timber.d("Photo ID: ${result.get().photoId}")
                Timber.d("Score: ${result.score}")
                Timber.d("Cosine Similarity: $cosineSimilarity")
                Timber.d("Similarity Condition: ${cosineSimilarity >= similarityThreshold}")

                cosineSimilarity >= similarityThreshold
            }

        Timber.d("Filtered Results Count: ${results.size}")

        return results
    }
}

package me.grey.picquery.domain

import java.util.Collections
import java.util.SortedSet
import java.util.TreeSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.data.dao.ImageSimilarityDao
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.ImageSimilarity
import me.grey.picquery.data.model.toFloatArray
import me.grey.picquery.domain.GroupSimilarPhotosUseCase.SimilarityNode
import timber.log.Timber

class GroupSimilarPhotosUseCase(
    private val embeddingRepository: EmbeddingRepository,
    private val similarityThreshold: Float = 0.96f,
    private val similarityDelta: Float = 0.02f,
    private val minGroupSize: Int = 2
) {

    data class SimilarityNode(
        val photoId: Long,
        val similarity: Float
    ) : Comparable<SimilarityNode> {
        override fun compareTo(other: SimilarityNode): Int = compareValuesBy(
            this,
            other,
            { it.similarity },
            { it.photoId }
        )
    }

    private suspend fun execute(
        photos: List<ImageSimilarity>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): List<List<SimilarityNode>> {
        val defers = mutableListOf<Deferred<List<List<SimilarityNode>>>>()
        val visit = Collections.synchronizedSet(mutableSetOf<Long>())
        val remainingPhotos = TreeSet<SimilarityNode>(
            compareBy({ it.similarity }, { it.photoId })
        )

        coroutineScope {
            photos.forEachIndexed { _, photo ->
                remainingPhotos.add(
                    SimilarityNode(photo.photoId, photo.similarityScore)
                )
            }

            for (photo in photos) {
                if (visit.contains(photo.photoId)) {
                    continue
                }

                val range =
                    photo.similarityScore - similarityDelta..photo.similarityScore + similarityDelta
                val similarPhotos = queryValuesInRange(
                    remainingPhotos,
                    SimilarityNode(-1, range.start),
                    SimilarityNode(-1, range.endInclusive)
                )
                visit.addAll(similarPhotos.map { it.photoId })
                remainingPhotos.removeAll(similarPhotos.toSet())
                Timber.tag("Similarity").d("Similar photos: $similarPhotos")
                if (similarPhotos.size >= minGroupSize) {
                    val inputs = similarPhotos.sortedBy { it.photoId }
                    // use doubleCheckSimilarity can also group similar photos
                    val lists = async(dispatcher) {
                        unionFindSimilarityGroups(
                            inputs,
                            similarityThreshold
                        )
                    }
                    defers.add(lists)
                }
            }
        }
        return defers.awaitAll().flatten()
    }

    /**
     * Group similar photos using Union Find algorithm.
     * @param photos The list of photos to group.
     * @param similarityThreshold The threshold to consider two photos as similar.
     * @return A list of similar photo groups.
     */
    private fun unionFindSimilarityGroups(
        photos: List<SimilarityNode>,
        similarityThreshold: Float = 0.95f
    ): List<List<SimilarityNode>> {
        val embeddings = embeddingRepository.getByPhotoIds(photos.map { it.photoId }.toLongArray())

        val unionFind = UnionFind(photos.size)

        for (i in photos.indices) {
            for (j in i + 1 until photos.size) {
                val similarity = calculateSimilarity(
                    embeddings[i].data.toFloatArray(),
                    embeddings[j].data.toFloatArray()
                )

                if (similarity >= similarityThreshold.toDouble()) {
                    unionFind.union(i, j)
                }
            }
        }

        return unionFind.getGroups().map { group ->
            group.map { index ->
                photos[index]
            }
        }
    }

    suspend operator fun invoke(photos: List<ImageSimilarity>): List<List<SimilarityNode>> = execute(photos)

    companion object {

        fun queryValuesInRange(
            treeSet: SortedSet<SimilarityNode>,
            lower: SimilarityNode,
            upper: SimilarityNode
        ): List<SimilarityNode> {
            val result = treeSet.subSet(lower, upper).toList()
            return result
        }
    }
}

class SimilarityManager(
    private val imageSimilarityDao: ImageSimilarityDao,
    private val embeddingRepository: EmbeddingRepository,
    private var similarityThreshold: Float = 0.96f,
    private var similarityDelta: Float = 0.02f,
    private var minGroupSize: Int = 2,
    private val pageSize: Int = 1000
) {
    // Update the method to include similarityDelta
    var groupSimilarPhotosUseCase = GroupSimilarPhotosUseCase(
        embeddingRepository,
        similarityThreshold,
        similarityDelta,
        minGroupSize
    )

    fun updateConfiguration(
        newSimilarityThreshold: Float? = null,
        newSimilarityDelta: Float? = null,
        newMinGroupSize: Int? = null
    ) {
        newSimilarityThreshold?.let { similarityThreshold = it }
        newSimilarityDelta?.let { similarityDelta = it }
        newMinGroupSize?.let { minGroupSize = it }

        // Recreate the use case with updated parameters
        groupSimilarPhotosUseCase = GroupSimilarPhotosUseCase(
            embeddingRepository,
            similarityThreshold,
            similarityDelta,
            minGroupSize
        )
        // Reset cached groups when configuration changes
        synchronized(cacheLock) {
            _cachedSimilarityGroups.clear()
            isFullyLoaded = false
        }
    }

    // Rest of the existing code remains the same
    private val _cachedSimilarityGroups = mutableListOf<List<SimilarityNode>>()
    private val cacheLock = Any()

    private var isFullyLoaded = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun groupSimilarPhotos(): Flow<List<SimilarityNode>> = flow {
        val cachedGroups = synchronized(cacheLock) {
            _cachedSimilarityGroups.toList()
        }

        if (cachedGroups.isNotEmpty() && isFullyLoaded) {
            emitAll(cachedGroups.asFlow())
            return@flow
        }

        synchronized(cacheLock) {
            _cachedSimilarityGroups.clear()
        }

        val allSimilarityGroups = mutableListOf<List<SimilarityNode>>()

        imageSimilarityDao.getAllSimilaritiesFlow(pageSize)
            .map { similaritiesPage ->
                val groupedSimilarities = groupSimilarPhotosUseCase(similaritiesPage)

                synchronized(cacheLock) {
                    _cachedSimilarityGroups.addAll(groupedSimilarities)
                    allSimilarityGroups.addAll(groupedSimilarities)
                }

                groupedSimilarities
            }
            .flatMapConcat { it.asFlow() }
            .collect { similarityGroup ->
                emit(similarityGroup)
            }

        synchronized(cacheLock) {
            isFullyLoaded = true
        }

        if (allSimilarityGroups.isEmpty()) {
            Timber.tag("SimilarityGrouper")
                .w("No similarity groups found, triggering recalculation")
        }
    }

    fun getSimilarityGroupByIndex(index: Int): List<SimilarityNode>? {
        synchronized(cacheLock) {
            if (!isFullyLoaded) return null

            return if (index in _cachedSimilarityGroups.indices) {
                _cachedSimilarityGroups[index]
            } else {
                null
            }
        }
    }

    fun clearCachedGroups() {
        synchronized(cacheLock) {
            _cachedSimilarityGroups.clear()
            isFullyLoaded = false
        }
    }

    @Suppress("unused")
    fun getAllCachedSimilarityGroups(): List<List<SimilarityNode>> {
        synchronized(cacheLock) {
            return if (isFullyLoaded) _cachedSimilarityGroups.toList() else emptyList()
        }
    }
}

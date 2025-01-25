package me.grey.picquery.domain

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.TreeSet

class GroupSimilarPhotosUseCase(
    private val embeddingRepository: EmbeddingRepository,
    private val similarityThreshold: Float = 0.02f,
    private val minGroupSize: Int = 2
) {

    data class SimilarityNode(
        val photoId: Long,
        val similarity: Float
    ) : Comparable<SimilarityNode> {
        override fun compareTo(other: SimilarityNode): Int =
            compareValuesBy(this, other,
                { it.similarity },
                { it.photoId }
            )
    }

    private fun execute(photos: List<ImageSimilarity>): List<List<SimilarityNode>> {
        val remainingPhotos = TreeSet<SimilarityNode>(
            compareBy({ it.similarity }, { it.photoId })
        )

        val visit = mutableSetOf<Long>()

        photos.forEachIndexed { _, photo ->
            remainingPhotos.add(
                SimilarityNode(photo.basePhotoId, photo.similarityScore)
            )
        }

        val similarGroups = mutableListOf<List<SimilarityNode>>()

        for (photo in photos) {
            if (visit.contains(photo.basePhotoId)) {
                continue
            }
            Log.d("Similarity", "Similar photo: $photo")

            val similarPhotos = queryValuesInRange(
                remainingPhotos,
                SimilarityNode(photo.comparedPhotoId, photo.similarityScore - similarityThreshold),
                SimilarityNode(photo.comparedPhotoId, photo.similarityScore + similarityThreshold)
            )
            visit.addAll(similarPhotos.map { it.photoId })
            remainingPhotos.removeAll(similarPhotos.toSet())
            Log.d("Similarity", "Similar photos: $similarPhotos")
            if (similarPhotos.size >= minGroupSize) {
                val inputs = similarPhotos.sortedBy { it.photoId }
                // use unionFindSimilarityGroups can also group similar photos
                val lists = unionFindSimilarityGroups(inputs)
                similarGroups.addAll(lists)
            }
        }
        return similarGroups
    }


    /**
     * Group similar photos using Union Find algorithm.
     * @param photos The list of photos to group.
     * @param similarityThreshold The threshold to consider two photos as similar.
     * @return A list of similar photo groups.
     */
    private fun unionFindSimilarityGroups(
        photos: List<SimilarityNode>,
        similarityThreshold: Float=0.98f,
    ): List<List<SimilarityNode>> {
        val uniquePhotos = photos.distinctBy { it.photoId }.sortedBy { it.photoId }
        val photoMap = uniquePhotos.withIndex().associate { it.value.photoId to it.index }
        val embeddings = embeddingRepository.getByPhotoIds(photos.map { it.photoId }.toLongArray()).sortedBy { it.photoId }

        val unionFind = UnionFind(uniquePhotos.size)

        for (i in uniquePhotos.indices) {
            for (j in i + 1 until uniquePhotos.size) {
                val photo1 = uniquePhotos[i]
                val photo2 = uniquePhotos[j]

                val similarity = calculateSimilarity(
                    embeddings[i].data.toFloatArray(),
                    embeddings[j].data.toFloatArray()
                )

                if (similarity >= similarityThreshold) {
                    unionFind.union(photoMap[photo1.photoId]!!, photoMap[photo2.photoId]!!)
                }
            }
        }

        return unionFind.getGroups().map { group ->
            group.map { index ->
                uniquePhotos[index]
            }
        }
    }


    /**
     * Double check the similarity of the photos in the group.
     * @param similarGroup The group of photos to check for similarity.
     * @param similarityThreshold The threshold to consider two photos as similar.
     * @return A list of similar photo groups.
     */
    private fun doubleCheckSimilarity(similarGroup: List<SimilarityNode>, similarityThreshold: Float = 0.98f): List<List<SimilarityNode>> {
        val embeddings = embeddingRepository.getByPhotoIds(similarGroup.map { it.photoId }.toLongArray()).sortedBy { it.photoId }
        
        // Create an adjacency list representation of the graph
        val graph = mutableMapOf<Long, MutableSet<Long>>()
        
        // Build graph edges based on similarity
        for (i in similarGroup.indices) {
            for (j in i + 1 until similarGroup.size) {
                val similarity = calculateSimilarity(
                    embeddings[i].data.toFloatArray(),
                    embeddings[j].data.toFloatArray()
                )
                
                Log.d("Similarity", "doubleCheckSimilarity: ${similarGroup[i].photoId} - ${similarGroup[j].photoId}: $similarity")
                
                if (similarity > similarityThreshold) {
                    val photoId1 = similarGroup[i].photoId
                    val photoId2 = similarGroup[j].photoId
                    
                    graph.getOrPut(photoId1) { mutableSetOf() }.add(photoId2)
                    graph.getOrPut(photoId2) { mutableSetOf() }.add(photoId1)
                }
            }
        }
        
        // Find connected components (similar photo groups)
        val visited = mutableSetOf<Long>()
        val similarGroups = mutableListOf<List<SimilarityNode>>()
        
        fun dfs(photoId: Long, currentGroup: MutableList<SimilarityNode>) {
            visited.add(photoId)
            currentGroup.add(similarGroup.first { it.photoId == photoId })
            
            graph[photoId]?.forEach { neighborId ->
                if (neighborId !in visited) {
                    dfs(neighborId, currentGroup)
                }
            }
        }
        
        // Perform DFS to find connected components
        for (node in similarGroup) {
            if (node.photoId !in visited) {
                val currentGroup = mutableListOf<SimilarityNode>()
                dfs(node.photoId, currentGroup)
                
                // Only add groups with at least 2 photos
                if (currentGroup.size > 1) {
                    similarGroups.add(currentGroup)
                }
            }
        }
        
        return similarGroups
    }

    operator fun invoke(photos: List<ImageSimilarity>): List<List<SimilarityNode>> =
        execute(photos)

    companion object {

        fun queryValuesInRange(treeSet: TreeSet<SimilarityNode>, lower: SimilarityNode, upper: SimilarityNode): List<SimilarityNode> {
            return treeSet.subSet(lower, true, upper, true).toList()
        }

    }
}



class SimilarityGrouper(
    private val imageSimilarityDao: ImageSimilarityDao,
    private val embeddingRepository: EmbeddingRepository,
    private val similarityThreshold: Float = 0.02f,
    private val minGroupSize: Int = 2,
    private val pageSize: Int = 1000
) {

    private val groupSimilarPhotosUseCase = GroupSimilarPhotosUseCase(embeddingRepository, similarityThreshold, minGroupSize)

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
                Log.d("SimilarityGrouper", "Similarity group emitted: $similarityGroup")
            }

        synchronized(cacheLock) {
            isFullyLoaded = true
        }

        if (allSimilarityGroups.isEmpty()) {
            Log.w("SimilarityGrouper", "No similarity groups found, triggering recalculation")
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

    fun getAllCachedSimilarityGroups(): List<List<SimilarityNode>> {
        synchronized(cacheLock) {
            return if (isFullyLoaded) _cachedSimilarityGroups.toList() else emptyList()
        }
    }

}
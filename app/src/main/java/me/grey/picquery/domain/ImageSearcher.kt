package me.grey.picquery.domain

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.R
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.common.encodeProgressCallback
import me.grey.picquery.common.loadThumbnail
import me.grey.picquery.common.preprocess
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.data.model.toFloatArray
import me.grey.picquery.domain.EmbeddingUtils.saveBitmapsToEmbedding
import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.TextEncoder
import timber.log.Timber

import java.util.Collections
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

enum class SearchTarget(val labelResId: Int, val icon: ImageVector) {
    Image(R.string.search_target_image, Icons.Outlined.ImageSearch),
    Text(R.string.search_target_text, Icons.Outlined.Translate),
}

class ImageSearcher(
    private val imageEncoder: ImageEncoder,
    private val textEncoder: TextEncoder,
    private val embeddingRepository: EmbeddingRepository,
    private val objectBoxEmbeddingRepository: ObjectBoxEmbeddingRepository,
    private val translator: MLKitTranslator,
    private val dispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "ImageSearcher"
        const val DEFAULT_MATCH_THRESHOLD = 0.20f
        const val DEFAULT_TOP_K = 30
        private const val SEARCH_BATCH_SIZE = 1000
    }

    val searchRange = mutableStateListOf<Album>()
    var isSearchAll = mutableStateOf(true)
    var searchTarget = mutableStateOf(SearchTarget.Image)

    val searchResultIds = mutableStateListOf<Long>()

    private val _matchThreshold = mutableFloatStateOf(DEFAULT_MATCH_THRESHOLD)
    val matchThreshold: State<Float> = _matchThreshold

    private val _topK = mutableIntStateOf(DEFAULT_TOP_K)
    val topK: State<Int> = _topK

    fun updateRange(range: List<Album>, searchAll: Boolean) {
        searchRange.clear()
        searchRange.addAll(range.sortedByDescending { it.count })
        isSearchAll.value = searchAll
    }

    suspend fun getBaseLine(): FloatArray {
        val whiteBenchmark =
            ResourcesCompat.getDrawable(context.resources, R.drawable.white_benchmark, null)
                ?.toBitmap()!!
        return imageEncoder.encodeBatch(listOf(whiteBenchmark)).first()
    }

    fun updateTarget(target: SearchTarget) {
        searchTarget.value = target
    }

    suspend fun hasEmbedding(): Boolean {
        return withContext(dispatcher) {
            val total = embeddingRepository.getTotalCount()
            Timber.tag(TAG).d("Total embedding count $total")
            total > 0
        }
    }

    private var encodingLock = false
    private var searchingLock = false

    /**
     * Encode all photos in the list and save to database.
     * @param photos List of photos to encode.
     * @param progressCallback Callback to report encoding progress.
     * @return True if encoding started, false if already encoding.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun encodePhotoListV2(
        photos: List<Photo>,
        progressCallback: encodeProgressCallback? = null,
    ): Boolean {
        if (encodingLock) {
            Timber.tag(TAG).w("encodePhotoListV2: Already encoding!")
            return false
        }
        Timber.tag(TAG).i("encodePhotoListV2 started.")
        encodingLock = true

        withContext(dispatcher) {
            val cur = AtomicInteger(0)
            Timber.tag(TAG).d("start: ${photos.size}")

            photos.asFlow()
                .map { photo ->
                    val thumbnailBitmap = loadThumbnail(context, photo)
                    if (thumbnailBitmap == null) {
                        Timber.tag(TAG).w("Unsupported file: '${photo.path}', skip encoding it.")
                        return@map null
                    }
                    val prepBitmap = preprocess(thumbnailBitmap)
                    PhotoBitmap(photo, prepBitmap)
                }
                .filterNotNull()
                .buffer(1000)

                .chunked(100)
                .onEach { Timber.tag(TAG).d("onEach: ${it.size}") }
                .onCompletion {
                    embeddingRepository.updateCache()
                    encodingLock = false
                }
                .collect {
                    val loops = 1
                    val batchSize = it.size / loops
                    val cost = measureTimeMillis {
                        val deferreds = (0 until loops).map { index ->
                            async {
                                val start = index * batchSize
                                if (start >= it.size) return@async
                                val end = start + batchSize
                                saveBitmapsToEmbedding(
                                    it.slice(start until end),
                                    imageEncoder,
                                    embeddingRepository,
                                    objectBoxEmbeddingRepository
                                )
                            }
                        }
                        deferreds.awaitAll()
                    }
                    cur.set(it.size)

                    progressCallback?.invoke(
                        cur.get(),
                        photos.size,
                        cost / it.size,
                    )
                    Timber.tag(TAG).d("cost: ${cost}")
                }
        }
        return true
    }

    suspend fun search(
        text: String,
        range: List<Album> = searchRange,
        onSuccess: suspend (MutableSet<MutableMap.MutableEntry<Double, Long>>) -> Unit,
    ) {
        translator.translate(
            text,
            onSuccess = { translatedText ->
                CoroutineScope(Dispatchers.Default).launch {
                    val res = searchWithRange(translatedText, range)
                    onSuccess(res)
                }
            },
            onError = {
                CoroutineScope(Dispatchers.Default).launch {
                    val res = searchWithRange(text, range)
                    onSuccess(res)
                }
                Timber.tag("MLTranslator").e("中文->英文翻译出错！\n${it.message}")
                showToast("翻译模型出错，请反馈给开发者！")
            },
        )
    }

    suspend fun searchV2(
        text: String,
        range: List<Album> = searchRange,
        onSuccess: suspend (MutableList<Pair<Long, Double>>) -> Unit,
    ) {
        translator.translate(
            text,
            onSuccess = { translatedText ->
                CoroutineScope(Dispatchers.Default).launch {
                    val res = searchWithRangeV2(translatedText, range)
                    onSuccess(res)
                }
            },
            onError = {
                CoroutineScope(Dispatchers.Default).launch {
                    val res = searchWithRangeV2(text, range)
                    onSuccess(res)
                }
                Timber.tag("MLTranslator").e("中文->英文翻译出错！\n${it.message}")
                showToast("翻译模型出错，请反馈给开发者！")
            },
        )
    }

    private suspend fun searchWithRange(
        text: String,
        range: List<Album> = searchRange
    ): MutableSet<MutableMap.MutableEntry<Double, Long>> {
        return withContext(dispatcher) {
            if (searchingLock) {
                return@withContext mutableSetOf()
            }
            searchingLock = true
            val textFeat = textEncoder.encode(text)
            val results = searchWithVector(range, textFeat)
            return@withContext results
        }
    }

    suspend fun searchWithRange(
        image: Bitmap,
        range: List<Album> = searchRange,
        onSuccess: suspend (MutableSet<MutableMap.MutableEntry<Double, Long>>) -> Unit,
    ) {
        return withContext(dispatcher) {
            if (searchingLock) {
                return@withContext
            }
            searchingLock = true
            val bitmapFeats = imageEncoder.encodeBatch(mutableListOf(image))
            val results = searchWithVector(range, bitmapFeats[0])
            onSuccess(results)
        }
    }

    private suspend fun searchWithRangeV2(
        text: String,
        range: List<Album> = searchRange
    ): MutableList<Pair<Long, Double>> {
        return withContext(dispatcher) {
            if (searchingLock) {
                return@withContext mutableListOf()
            }
            searchingLock = true
            val textFeat = textEncoder.encode(text)
            Timber.tag(TAG).d("Text feature: ${textFeat.joinToString(",")}")
            val results = searchWithVectorV2(range, textFeat)
            return@withContext results
        }
    }
    suspend fun searchWithRangeV2(
        image: Bitmap,
        range: List<Album> = searchRange,
        onSuccess: suspend (MutableList<Pair<Long, Double>>) -> Unit,
    ) {
        return withContext(dispatcher) {
            if (searchingLock) {
                return@withContext
            }
            searchingLock = true
            val bitmapFeats = imageEncoder.encodeBatch(mutableListOf(image))
            val results = searchWithVectorV2(range, bitmapFeats[0])
            onSuccess(results)
        }
    }

    private suspend fun searchWithVectorV2(
        range: List<Album>,
        textFeat: FloatArray
    ): MutableList<Pair<Long, Double>> = withContext(dispatcher) {
        try {
            searchingLock = true

            Timber.tag(TAG).d("Search with vector V2")

            val albumIds = if (range.isEmpty() || isSearchAll.value) {
                Timber.tag(TAG).d("Search from all album")
                null
            } else {
                Timber.tag(TAG).d("Search from: [${range.joinToString { it.label }}]")
                range.map { it.id }
            }

            val searchResults = objectBoxEmbeddingRepository.searchNearestVectors(
                queryVector = textFeat,
                topK = DEFAULT_TOP_K,
                similarityThreshold = matchThreshold.value,
                albumIds = albumIds
            )
            Timber.tag(TAG).d("Search result: found ${searchResults.size} pics")

            searchResultIds.clear()
            val ans = mutableListOf<Long>()
            searchResults.forEachIndexed { _, pair ->
                ans.add(pair.get().photoId)
            }
            searchResultIds.addAll(ans)

            Timber.tag(TAG).d("Search result: found ${ans.size} pics")
            return@withContext searchResults.map { it.get().photoId to it.score }.toMutableList()

        } finally {
            searchingLock = false
        }
    }

    private suspend fun searchWithVector(
        range: List<Album>,
        textFeat: FloatArray
    ): MutableSet<MutableMap.MutableEntry<Double, Long>> = withContext(dispatcher) {
        try {
            searchingLock = true
            val threadSafeSortedMap = Collections.synchronizedSortedMap(
                TreeMap<Double, Long>(compareByDescending { it })
            )

            val embeddings = if (range.isEmpty() || isSearchAll.value) {
                Timber.tag(TAG).d("Search from all album")
                embeddingRepository.getAllEmbeddingsPaginated(SEARCH_BATCH_SIZE)
            } else {
                Timber.tag(TAG).d("Search from: [${range.joinToString { it.label }}]")
                embeddingRepository.getEmbeddingsByAlbumIdsPaginated(
                    range.map { it.id },
                    SEARCH_BATCH_SIZE
                )
            }

            var totalProcessed = 0
            embeddings.collect { chunk ->
                Timber.tag(TAG).d("Processing chunk: ${chunk.size}")
                totalProcessed += chunk.size

                for (emb in chunk) {
                    val sim = calculateSimilarity(emb.data.toFloatArray(), textFeat)
                    Timber.tag(TAG).d("similarity: ${emb.photoId} -> $sim")
                    if (sim >= matchThreshold.value) {
                        insertDescendingThreadSafe(threadSafeSortedMap, Pair(emb.photoId, sim))
                    }
                }
            }

            Timber.tag(TAG).d("Search Finish: Processed $totalProcessed embeddings")
            Timber.tag(TAG).d("Search result: found ${threadSafeSortedMap.size} pics")

            searchResultIds.clear()
            mutableSetOf<MutableMap.MutableEntry<Double, Long>>().apply {
                addAll(threadSafeSortedMap.entries)
                searchResultIds.addAll(threadSafeSortedMap.values)
                Timber.tag(TAG).d("Search result: ${joinToString(",")}")
                return@withContext this
            }
        } finally {
            searchingLock = false
        }
    }

    // Thread-safe version of insertDescending
    private fun insertDescendingThreadSafe(
        map: SortedMap<Double, Long>,
        candidate: Pair<Long, Double>
    ) {
        if (map.size >= DEFAULT_TOP_K) {
            val min = map.lastKey()
            if (candidate.second >= min) {
                map[candidate.second] = candidate.first
                map.remove(min)
            }
        } else {
            map[candidate.second] = candidate.first
        }
    }

    fun updateSearchConfiguration(newMatchThreshold: Float, newTopK: Int) {
        _matchThreshold.floatValue = newMatchThreshold.coerceIn(0.1f, 0.5f)

        _topK.intValue = newTopK.coerceIn(10, 100)

        Timber.tag(TAG).d(
            "Search configuration updated: " +
                    "matchThreshold=${_matchThreshold.floatValue}, topK=${_topK.intValue}"
        )
    }
}
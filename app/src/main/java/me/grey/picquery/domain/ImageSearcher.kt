package me.grey.picquery.domain

import android.content.ContentResolver
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.grey.picQuery.core.ioDispatcher
import com.grey.picquery.library.ImageEncoder
import com.grey.picquery.library.textencoder.TextEncoder
import me.grey.picquery.common.loadThumbnail
import me.grey.picquery.common.preprocess
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.data.model.toFloatArray
import me.grey.picquery.domain.EmbeddingUtils.saveBitmapsToEmbedding

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
    private val contentResolver: ContentResolver,
    private val translator: MLKitTranslator,
) {
    companion object {
        private const val TAG = "ImageSearcher"
        private const val DEFAULT_MATCH_THRESHOLD = 0.01
        private const val TOP_K = 30
    }

    val searchRange = mutableStateListOf<Album>()
    var isSearchAll = mutableStateOf(true)
    var searchTarget = mutableStateOf(SearchTarget.Image)

    val searchResultIds = mutableStateListOf<Long>()
    private val sorteSimiliaritydMap: SortedMap<Double, Long> = TreeMap(compareByDescending { it })

    // 相似度阈值，一般在0.25以上
    private val matchThreshold = mutableFloatStateOf(DEFAULT_MATCH_THRESHOLD.toFloat())

    fun updateRange(range: List<Album>, searchAll: Boolean) {
        searchRange.clear()
        searchRange.addAll(range.sortedByDescending { it.count })
        isSearchAll.value = searchAll
    }

    fun updateTarget(target: SearchTarget) {
        searchTarget.value = target
    }

    suspend fun hasEmbedding(): Boolean {
        return withContext(Dispatchers.IO) {
            val total = embeddingRepository.getTotalCount()
            Log.d(TAG, "Total embedding count $total")
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
        progressCallback: encodeProgressCallback? = null
    ): Boolean {
        if (encodingLock) {
            Log.w(TAG, "encodePhotoListV2: Already encoding!")
            return false
        }
        Log.i(TAG, "encodePhotoListV2 started.")
        encodingLock = true

        withContext(ioDispatcher) {
            val cur = AtomicInteger(0)
            Log.d(TAG, "start: ${photos.size}")
            Log.d("encodePhotoListV2", "start: ${System.currentTimeMillis()}")

            photos.asFlow()
                .map { photo ->
                    val thumbnailBitmap = loadThumbnail(context, photo)
                    if (thumbnailBitmap == null) {
                        Log.w(TAG, "Unsupported file: '${photo.path}', skip encoding it.")
                        return@map null
                    }
                    val prepBitmap = preprocess(thumbnailBitmap)
                    Log.d(TAG, "prepBitmap: ${prepBitmap.width}x${prepBitmap.height}")
                    PhotoBitmap(photo, prepBitmap)
                }
                .filterNotNull()
                .buffer(1000)
                .chunked(40)
                .onEach { Log.d(TAG, "onEach: ${it.size}") }
                .onCompletion {
                    Log.d(TAG, "onCompletion: ${it}")
                    Log.d("encodePhotoListV2", "onCompletion: ${System.currentTimeMillis()}")
                    progressCallback?.invoke(photos.size, photos.size, 0)
                    embeddingRepository.updateCache()
                    encodingLock = false

                }
                .collect {
                    val loops = 4
                    val batchSize = it.size / loops
                    val cost = measureTimeMillis {
                        val deferreds = (0 until loops).map { index ->
                            async {
                                val start = index * batchSize
                                if (start>= it.size) return@async
                                val end = start + batchSize
                                saveBitmapsToEmbedding(
                                    it.slice(start until end),
                                    imageEncoder,
                                    embeddingRepository
                                )
                            }
                        }
                        deferreds.awaitAll()
                    }
                    progressCallback?.invoke(
                        cur.get(),
                        photos.size,
                        cost / it.size,
                    )
                    Log.d(TAG, "cost: ${cost}")
                    cur.addAndGet(it.size)
                }
        }
        return true
    }


    suspend fun search(
        text: String,
        range: List<Album> = searchRange,
        onSuccess: suspend (List<Long>?) -> Unit,
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
                Log.e("MLTranslator", "中文->英文翻译出错！\n${it.message}")
                showToast("翻译模型出错，请反馈给开发者！")
            },
        )
    }

    private suspend fun searchWithRange(
        text: String,
        range: List<Album> = searchRange
    ): List<Long>? {
        return withContext(Dispatchers.Default) {
            if (searchingLock) {
                return@withContext null
            }
            searchingLock = true
            val textFeat = textEncoder.encode(text)
            Log.d(TAG, "Encode text: '${text}'")
            val embeddings = if (range.isEmpty() || isSearchAll.value) {
                Log.d(TAG, "Search from all album")
                embeddingRepository.getAll()
            } else {
                Log.d(TAG, "Search from: [${range.joinToString { it.label }}]")
                embeddingRepository.getByAlbumList(range)
            }
            Log.d(TAG, "Get all ${embeddings.size} photo embeddings done")
            sorteSimiliaritydMap.clear()
            for (emb in embeddings) {
                val sim = calculateSimilarity(emb.data.toFloatArray(), textFeat)
                Log.d(TAG, "similarity: ${emb.photoId} -> $sim")
                if (sim >= matchThreshold.floatValue) {
                    insertDescending(Pair(emb.photoId, sim))
                }
            }
            searchingLock = false
            Log.d(TAG, "Search result: found ${sorteSimiliaritydMap.size} pics")

            searchResultIds.clear()
            val results = mutableListOf<Long>()
            sorteSimiliaritydMap.forEach {
                results.add(it.value)
            }
            searchResultIds.addAll(results)
            Log.d(TAG, "Search result: ${results.joinToString(",")}")
            return@withContext results
        }
    }

    // use red black tree to keep top k
    private fun insertDescending(candidate: Pair<Long, Double>) {
        if (sorteSimiliaritydMap.size >= TOP_K) {
            val min = sorteSimiliaritydMap.lastKey()
            if (candidate.second >= min) {
                sorteSimiliaritydMap[candidate.second] = candidate.first
                sorteSimiliaritydMap.remove(min)
            }
        } else {
            sorteSimiliaritydMap[candidate.second] = candidate.first
        }
    }
}
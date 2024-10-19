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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.R
import me.grey.picquery.common.ObjectPool
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.common.encodeProgressCallback
import me.grey.picquery.common.loadThumbnail
import me.grey.picquery.common.preprocess
import me.grey.picquery.common.showToast
import me.grey.picquery.common.toFloatArray
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.domain.EmbeddingUtils.saveBtimapsToEmbedings
import me.grey.picquery.domain.encoder.ImageEncoder
import me.grey.picquery.domain.encoder.TextEncoder
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
        private const val DEFAULT_MATCH_THRESHOLD = 0.15f
        private const val TOP_K = 30
    }

    val searchRange = mutableStateListOf<Album>()
    var isSearchAll = mutableStateOf(true)
    var searchTarget = mutableStateOf(SearchTarget.Image)

    val searchResultIds = mutableStateListOf<Long>()

    // 相似度阈值，一般在0.25以上
    private val matchThreshold = mutableFloatStateOf(DEFAULT_MATCH_THRESHOLD)

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
     * 使用 kotlin flow 来处理图片编码流程
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
        imageEncoder.loadModel()

        try {
            withContext(Dispatchers.Default) {
                var cur = AtomicInteger(0)
                Log.d(TAG, "start: ${photos.size}")
                Log.d("encodePhotoListV2", "start: ${System.currentTimeMillis()}")

                photos.asFlow()
                    .chunked(5)
                    .map { photos ->
                        val deferredResults = mutableListOf<Deferred<PhotoBitmap?>>()
                        photos.forEach { photo ->
                            val deferred = async {
                                val thumbnailBitmap = loadThumbnail(context, photo)
                                if (thumbnailBitmap == null) {
                                    Log.w(TAG, "Unsupported file: '${photo.path}', skip encoding it.")
                                    return@async null
                                }
                                val prepBitmap = preprocess(thumbnailBitmap)
                                Log.d(TAG, "prepBitmap: ${prepBitmap.width}x${prepBitmap.height}")
                                PhotoBitmap(photo, prepBitmap)
                            }
                            deferredResults.add(deferred)
                        }
                        awaitAll(*deferredResults.toTypedArray())
                        deferredResults.mapNotNull { it.getCompleted() }
                    }
                    .flatMapConcat { it.asFlow() }
                    .filterNotNull()
                    .buffer(1000)
                    .chunked(10)
                    .onCompletion {
                        Log.d(TAG, "onCompletion: ${it}")
                        Log.d("encodePhotoListV2", "onCompletion: ${System.currentTimeMillis()}")
                        progressCallback?.invoke(photos.size, photos.size, 0)
                    }
                    .collect {
                        try {
                            val cost = measureTimeMillis {
                                saveBtimapsToEmbedings(it, ObjectPool.ImageEncoderPool.acquire(), embeddingRepository)
                            }
                            progressCallback?.invoke(
                                cur.get(),
                                photos.size,
                                cost/it.size,
                            )
                            cur.addAndGet(it.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "collect: ${e.message}")
                        }

                        Log.d(TAG, "collect: ${cur}")
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "encodePhotoListV2: Coroutine was cancelled", e)
        } finally {
            encodingLock = false
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
            val photoResults = mutableListOf<Pair<Long, Double>>()
            val embeddings = if (range.isEmpty() || isSearchAll.value) {
                Log.d(TAG, "Search from all album")
                embeddingRepository.getAll()
            } else {
                Log.d(TAG, "Search from: [${range.joinToString { it.label }}]")
                embeddingRepository.getByAlbumList(range)
            }
            Log.d(TAG, "Get all ${embeddings.size} photo embeddings done")
            for (emb in embeddings) {
                val sim = calculateSimilarity(emb.data.toFloatArray(), textFeat)
                if (sim >= matchThreshold.floatValue) {
                    insertDescending(photoResults, Pair(emb.photoId, sim))
                }
            }
            searchingLock = false
            Log.d(TAG, "Search result: found ${photoResults.size} pics")
            Log.d(TAG, "photoResults: ${photoResults.joinToString()}")

            searchResultIds.clear()
            searchResultIds.addAll(photoResults.map { it.first })
            return@withContext photoResults.map { it.first }
        }
    }

    // 将结果替换已有序列中最小的位置，保持结果降序排列
    private fun insertDescending(
        resultPair: MutableList<Pair<Long, Double>>,
        candidate: Pair<Long, Double>
    ) {
        if (resultPair.isEmpty()) {
            resultPair.add(candidate)
            return
        }
        val smallestIndex = resultPair.indexOfFirst { it.second < candidate.second }
        if (smallestIndex == -1 && resultPair.size < TOP_K) {
            resultPair.add(candidate)
        } else if (smallestIndex != -1) {
            resultPair.add(smallestIndex, candidate)
            if (resultPair.size > TOP_K) {
                resultPair.removeLast()
            }
        }
    }
}
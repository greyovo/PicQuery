package me.grey.picquery.domain

import android.content.ContentResolver
import android.graphics.Bitmap
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.R
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.common.encodeProgressCallback
import me.grey.picquery.common.showToast
import me.grey.picquery.common.toByteArray
import me.grey.picquery.common.toFloatArray
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.encoder.IMAGE_INPUT_SIZE
import me.grey.picquery.domain.encoder.ImageEncoder
import me.grey.picquery.domain.encoder.TextEncoder
import java.nio.FloatBuffer
import java.util.Timer
import java.util.TimerTask

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
        private const val DEFAULT_MATCH_THRESHOLD = 0.25f
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

    suspend fun encodePhotoList(
        photos: List<Photo>,
        progressCallback: encodeProgressCallback? = null
    ): Boolean {
        if (encodingLock) {
            Log.w(TAG, "encodePhotoList: Already encoding!")
            return false
        }
        encodingLock = true
        val listToUpdate = mutableListOf<Embedding>()

        var count = 0
        var startTime: Long
        var cost = 0L

        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (count > 0) {
                    progressCallback?.invoke(
                        count,
                        photos.size,
                        cost,
                    )
                }
            }
        }, 50, 500)

        for (photo in photos) {
//            Log.d(TAG, photo.toString())
            // ====
//            Log.d(TAG, "Use: contentResolver")
//            val start = System.currentTimeMillis() // REMOVE
            startTime = System.currentTimeMillis()
            val thumbnailBitmap = contentResolver.loadThumbnail(photo.uri, IMAGE_INPUT_SIZE, null)
//            Log.d(TAG, "load: ${System.currentTimeMillis() - start}ms") // REMOVE
            val feat: FloatBuffer = imageEncoder.encode(thumbnailBitmap)
            listToUpdate.add(
                Embedding(
                    photoId = photo.id,
                    albumId = photo.albumID,
                    data = feat.toByteArray()
                )
            )
            count++
            cost = System.currentTimeMillis() - startTime
        }
        embeddingRepository.updateAll(listToUpdate)
        encodingLock = false
        progressCallback?.invoke(
            count,
            photos.size,
            cost
        )
        timer.cancel()
        return true
    }

    suspend fun encodeBatch(imageBitmaps: List<Bitmap>) {
        if (encodingLock) {
            Log.w(TAG, "encodePhotoList: Already encoding!")
            return
        }
        encodingLock = true
        val batchResult = mutableListOf<Embedding>()
        for (bitmap in imageBitmaps) {
            val feat: FloatBuffer = imageEncoder.encode(bitmap)
            batchResult.add(Embedding(photoId = 11, albumId = 11, data = feat.toByteArray()))
        }
        embeddingRepository.updateAll(batchResult)
        encodingLock = false
    }

    suspend fun searchWithChinese(
        text: String,
        range: List<Album> = searchRange,
        onSuccess: suspend (List<Long>?) -> Unit,
    ) {
        translator.translate(
            text,
            onSuccess = { translatedText ->
                CoroutineScope(Dispatchers.Default).launch {
                    val res = search(translatedText, range)
                    onSuccess(res)
                }
            },
            onError = {
                CoroutineScope(Dispatchers.Default).launch {
                    val res = search(text, range)
                    onSuccess(res)
                }
                Log.e("MLTranslator", "中文->英文翻译出错！\n${it.message}")
                showToast("翻译模型出错，请反馈给开发者！")
            },
        )
    }

    suspend fun search(text: String, range: List<Album> = searchRange): List<Long>? {
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
//            Log.i(TAG, "imageFeat ${emb.data.toFloatArray().joinToString()}")
                val sim = calculateSimilarity(emb.data.toFloatArray(), textFeat)
//            val sim = sphericalDistLoss(emb.data.toFloatArray(), textFeat)
//            val sim = Cosine.similarity(emb.data.toFloatArray(), textFeat)
//                insertDescending(photoResults, Pair(emb.photoId, sim))
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
        if (smallestIndex == -1) {
            // 如果没有找到，有两种情况：
            // 1. 数组满了，则返回，什么都不做
            // 2. 数组没满，则直接插入在最末尾
            if (resultPair.size < TOP_K) {
                resultPair.add(candidate)
            }
        } else {
            resultPair.add(smallestIndex, candidate)
            if (resultPair.size > TOP_K) {
                resultPair.removeLast()
            }
        }
    }


    private fun selectMax() {

    }

    private fun searchByEmbedding() {

    }


}
package me.grey.picquery.core

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.common.Cosine
import me.grey.picquery.core.encoder.ImageEncoder
import me.grey.picquery.core.encoder.TextEncoder
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.Photo
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.common.onProgressCallback
import me.grey.picquery.common.sphericalDistLoss
import me.grey.picquery.common.toByteArray
import me.grey.picquery.common.toFloatArray
import me.grey.picquery.core.encoder.IMAGE_INPUT_SIZE
import me.grey.picquery.data.model.Album
import java.nio.FloatBuffer


object ImageSearcher {
    private var embeddingRepository = EmbeddingRepository()

    private var imageEncoder: ImageEncoder? = null
    private var textEncoder: TextEncoder? = null

    private const val TAG = "ImageSearcher"
    private const val MATCH_THRESHOLD = 0.25
    private const val TOP_K = 30

    private val contentResolver = PicQueryApplication.context.contentResolver

    private fun loadImageEncoder() {
        if (imageEncoder == null) {
            imageEncoder = ImageEncoder
        }
    }

    private fun loadTextEncoder() {
        if (textEncoder == null) {
            textEncoder = TextEncoder
        }
    }

    private var encodingLock = false
    private var searchingLock = false

    fun encodePhotoList(
        photos: List<Photo>,
        progressCallback: onProgressCallback? = null
    ): Boolean {
        loadImageEncoder()

        if (encodingLock) {
            Log.w(TAG, "encodePhotoList: Already encoding!")
            return false
        }
        encodingLock = true
        var count = 0
        val listToUpdate = mutableListOf<Embedding>()
        for (photo in photos) {
            Log.d(TAG, photo.toString())
            // ====
//            Log.d(TAG, "Use: contentResolver")
//            val start = System.currentTimeMillis() // REMOVE
            val thumbnailBitmap = contentResolver.loadThumbnail(photo.uri, IMAGE_INPUT_SIZE, null)
//            Log.d(TAG, "load: ${System.currentTimeMillis() - start}ms") // REMOVE
            val feat: FloatBuffer = imageEncoder!!.encode(thumbnailBitmap)
            listToUpdate.add(
                Embedding(
                    photoId = photo.id,
                    albumId = photo.albumID,
                    data = feat.toByteArray()
                )
            )
            count++
            progressCallback?.invoke(count, photos.size)
        }
        embeddingRepository.updateAll(listToUpdate)
        encodingLock = false
        return true
    }

    fun encodeBatch(imageBitmaps: List<Bitmap>) {
        loadImageEncoder()

        if (encodingLock) {
            Log.w(TAG, "encodePhotoList: Already encoding!")
            return
        }
        encodingLock = true
        val batchResult = mutableListOf<Embedding>()
        for (bitmap in imageBitmaps) {
            val feat: FloatBuffer = imageEncoder!!.encode(bitmap)
            batchResult.add(Embedding(photoId = 11, albumId = 11, data = feat.toByteArray()))
        }
        embeddingRepository.updateAll(batchResult)
        encodingLock = false
    }

    private fun encode(bitmap: Bitmap) {
    }


    suspend fun search(text: String, range: List<Album> = emptyList()): List<Long>? {
        return withContext(Dispatchers.Default) {
            loadTextEncoder()
            if (searchingLock) {
                return@withContext null
            }
            searchingLock = true
            val textFeat = textEncoder!!.encode(text)
            Log.d(TAG, "Encode '${text}' done")
            Log.i(TAG, "textFeat ${textFeat.joinToString()}")
            val photoResults = mutableListOf<Pair<Long, Double>>()
            val embeddings = embeddingRepository.getAll()
            Log.d(TAG, "Get all ${embeddings.size} photo embeddings done")
            for (emb in embeddings) {
//            Log.i(TAG, "imageFeat ${emb.data.toFloatArray().joinToString()}")
                val sim = calculateSimilarity(emb.data.toFloatArray(), textFeat)
//            val sim = sphericalDistLoss(emb.data.toFloatArray(), textFeat)
//            val sim = Cosine.similarity(emb.data.toFloatArray(), textFeat)
                insertSmallest(photoResults, Pair(emb.photoId, sim))
            }
            searchingLock = false
            Log.d(TAG, "Search result: found ${photoResults.size} pics")
            Log.d(TAG, "photoResults: ${photoResults.joinToString()}")

            return@withContext photoResults.map { it.first }
        }
    }

    // 将结果替换已有序列中最小的位置，保持结果降序排列
    private fun insertSmallest(
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
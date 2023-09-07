package me.grey.picquery.core

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.core.encoder.ImageEncoder
import me.grey.picquery.core.encoder.TextEncoder
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.Photo
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.common.onProgressCallback
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
    private const val MATCH_THRESHOLD = 0.2

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


    fun search(text: String, range: List<Album> = emptyList()): List<Long>? {
        loadTextEncoder()
        if (searchingLock) {
            return null
        }
        searchingLock = true
        val textFeat = textEncoder!!.encode(text)
        Log.d(TAG, "Encode text=${text} done")
        val resultPhotoIds = mutableListOf<Long>()
        val embeddings = embeddingRepository.getAll()
        Log.d(TAG, "Get all ${embeddings.size} photo embeddings done")
        for (emb in embeddings) {
            val sim = calculateSimilarity(emb.data.toFloatArray(), textFeat)
            if (sim >= MATCH_THRESHOLD) {
                resultPhotoIds.add(emb.photoId)
            }
        }
        searchingLock = false
        Log.d(TAG, "Search result: found ${resultPhotoIds.size} pics")
        return resultPhotoIds
    }

    private fun selectMax() {

    }

    private fun searchByEmbedding() {

    }


}
package me.grey.picquery.core

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import me.grey.picquery.core.encoder.ImageEncoder
import me.grey.picquery.core.encoder.TextEncoder
import me.grey.picquery.data.ImageEmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.Photo
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.common.decodeSampledBitmapFromFile
import me.grey.picquery.common.toByteArray
import me.grey.picquery.common.toFloatArray
import me.grey.picquery.core.encoder.IMAGE_INPUT_SIZE
import java.io.*
import java.nio.FloatBuffer


class ImageSearcher(
    private val imageEncoder: ImageEncoder,
    private val textEncoder: TextEncoder,
    embeddingDirectory: File
) {
    private var imageEmbeddingRepository = ImageEmbeddingRepository()

    companion object {
        private const val TAG = "ImageSearcher"
        private const val MATCH_THRESHOLD = 0.3
    }

    fun encodePhotoList(contentResolver: ContentResolver, photos: List<Photo>, context: Context?) {
        val listToUpdate = mutableListOf<Embedding>()
        for (photo in photos) {
            Log.d(TAG, photo.toString())
            // ====
            Log.d(TAG, "Use: contentResolver")
            val start = System.currentTimeMillis() // REMOVE
            val thumbnailBitmap =
                contentResolver.loadThumbnail(photo.uri, IMAGE_INPUT_SIZE, null)
            Log.d(TAG, "load: ${System.currentTimeMillis() - start}ms") // REMOVE
            val feat: FloatBuffer = imageEncoder.encode(thumbnailBitmap)
            listToUpdate.add(
                Embedding(
                    photoId = photo.id,
                    albumId = photo.albumID,
                    data = feat.toByteArray()
                )
            )
        }
        imageEmbeddingRepository.updateAll(listToUpdate)
    }

    fun encodeBatch(imageBitmaps: List<Bitmap>) {
        val batchResult = mutableListOf<Embedding>()
        for (bitmap in imageBitmaps) {
            val feat: FloatBuffer = imageEncoder.encode(bitmap)
            batchResult.add(Embedding(photoId = 11, albumId = 11, data = feat.toByteArray()))
        }
        imageEmbeddingRepository.updateAll(batchResult)
    }

    private fun encode(bitmap: Bitmap) {
    }


    fun search(text: String): List<Long> {
        val textFeat = textEncoder.encode(text)
        val resultPhotoIds = mutableListOf<Long>()
        val embeddings = imageEmbeddingRepository.getAll()
        for (emb in embeddings) {
            val sim = calculateSimilarity(emb.data.toFloatArray(), textFeat)
            if (sim >= MATCH_THRESHOLD) {
                resultPhotoIds.add(emb.photoId)
            }
        }
        return resultPhotoIds
    }

    private fun selectMax() {

    }

    private fun searchByEmbedding() {

    }


}
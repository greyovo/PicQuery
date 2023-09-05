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
import java.io.*
import java.nio.FloatBuffer


class ImageSearcher(
    private val imageEncoder: ImageEncoder,
    private val textEncoder: TextEncoder,
    embeddingDirectory: File
) {
    private var imageEmbeddingRepository = ImageEmbeddingRepository(embeddingDirectory)

    companion object {
        private const val TAG = "ImageSearcher"
        private const val MATCH_THRESHOLD = 0.3
    }

    fun encodePhotoList(contentResolver: ContentResolver, photos: List<Photo>, context: Context?) {
        for (photo in photos) {
            Log.d(TAG, photo.toString())
            // ====
            val thumbnailBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Use: contentResolver")
                val start = System.currentTimeMillis() // REMOVE
                val res = contentResolver.loadThumbnail(photo.uri, ImageEncoder.INPUT_SIZE, null)
                Log.d(TAG, "load: ${System.currentTimeMillis() - start}ms") // REMOVE
                res
            } else {
                Log.d(TAG, "Use: decodeSampledBitmapFromFileDescriptor")
                val start = System.currentTimeMillis() // REMOVE
                val res = decodeSampledBitmapFromFile(photo.path, ImageEncoder.INPUT_SIZE)
                Log.d(TAG, "load: ${System.currentTimeMillis() - start}ms") // REMOVE
                res
            }
//            context?.let { saveBitMap(it, thumbnailBitmap, photo.label) }
            val feat: FloatBuffer = imageEncoder.encode(thumbnailBitmap)
            imageEmbeddingRepository.update(Embedding(id = photo.id, data = feat.array()))
        }
    }

    fun encodeBatch(imageBitmaps: List<Bitmap>) {
        val batchResult = mutableListOf<Embedding>()
        for (bitmap in imageBitmaps) {
            val feat: FloatBuffer = imageEncoder.encode(bitmap)
            batchResult.add(Embedding(id = 11, data = feat.array()))
        }
        imageEmbeddingRepository.updateAll(batchResult)
    }

    private fun encode(bitmap: Bitmap) {
    }


    fun search(text: String): List<Long> {
        val textFeat = textEncoder.encode(text)
        val resultPhotoIds = mutableListOf<Long>()
        for ((id, imgFeat) in imageEmbeddingRepository.embeddingMap) {
            val sim = calculateSimilarity(imgFeat.data, textFeat)
            if (sim >= MATCH_THRESHOLD) {
                resultPhotoIds.add(id)
            }
        }
        return resultPhotoIds
    }

    private fun selectMax() {

    }

    private fun searchByEmbedding() {

    }


}
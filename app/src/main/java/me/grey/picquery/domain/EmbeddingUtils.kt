package me.grey.picquery.domain

import android.util.Log

import kotlinx.coroutines.coroutineScope
import me.grey.picquery.common.ObjectPool
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.data.model.toByteArray
import me.grey.picquery.domain.encoder.ImageEncoder
import java.nio.FloatBuffer
import kotlin.system.measureTimeMillis

object EmbeddingUtils {

    val TAG = "EmbeddingUtils"
    suspend fun saveBitmapToEmbedding(
        item: PhotoBitmap,
        imageEncoder: ImageEncoder,
        embeddingRepository: EmbeddingRepository
    ) {
        Log.d(TAG, "Start encoding for embedding...")
        Log.d(TAG, "${System.currentTimeMillis()} Start encoding image...")
        val time = measureTimeMillis {
            val feat: FloatBuffer = imageEncoder.encode(item.bitmap, usePreprocess = false)
            Log.d(TAG, "${System.currentTimeMillis()} end encoding image...")
            ObjectPool.ImageEncoderPool.release(imageEncoder)
            embeddingRepository.updateList(Embedding(
                photoId = item.photo.id,
                albumId = item.photo.albumID,
                data = feat.array().toByteArray(),
            ))
        }
        Log.d(TAG, "Encode[v2] done! Time: $time")
    }

    suspend fun saveBitmapsToEmbedding(
        items: List<PhotoBitmap?>,
        imageEncoder: ImageEncoder,
        embeddingRepository: EmbeddingRepository
    ) {
        coroutineScope {
            Log.d(TAG, "saveBitmapsToEmbeddings Start encoding for embedding...")

            Log.d(TAG, "Start encoding for embedding...")
            Log.d(TAG, "${System.currentTimeMillis()} Start encoding image...")
            val time = measureTimeMillis {
                val embeddings = imageEncoder.encodeBatch(items.map { it!!.bitmap }, usePreprocess = false)
                Log.d(TAG, "${System.currentTimeMillis()} end encoding image...")
                ObjectPool.ImageEncoderPool.release(imageEncoder)
                embeddings.forEachIndexed { index, feat ->
                    embeddingRepository.updateList(Embedding(
                        photoId = items[index]!!.photo.id,
                        albumId = items[index]!!.photo.albumID,
                        data = feat.toByteArray()
                    ))
                }
            }
            Log.d(TAG, "Encode[v2] done! Time: $time")
            Log.d(TAG, "saveBtimapsToEmbedings    Encode[v2] done!")
        }
    }

}
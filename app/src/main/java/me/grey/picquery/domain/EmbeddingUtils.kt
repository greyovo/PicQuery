package me.grey.picquery.domain

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import me.grey.picquery.common.ObjectPool
import me.grey.picquery.common.toByteArray
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.domain.encoder.ImageEncoder
import java.nio.FloatBuffer

object EmbeddingUtils {

    val TAG = "EmbeddingUtils"
    suspend fun saveBitmapToEmbedding(
        item: PhotoBitmap,
        imageEncoder: ImageEncoder,
        embeddingRepository: EmbeddingRepository
    ) {
        Log.d(TAG, "Start encoding for embedding...")
        val embListResult = mutableListOf<Embedding>()
        val feat: FloatBuffer = imageEncoder.encode(item.bitmap, usePreprocess = false)
        ObjectPool.ImageEncoderPool.release(imageEncoder)
        embListResult.add(
            Embedding(
                photoId = item.photo.id,
                albumId = item.photo.albumID,
                data = feat.toByteArray()
            )
        )
        embeddingRepository.updateAll(embListResult)
        Log.d(TAG, "Encode[v2] done!")
    }

    suspend fun CoroutineScope.saveBtimapsToEmbedings(it: List<PhotoBitmap?>, imageEncoder: ImageEncoder, embeddingRepository: EmbeddingRepository) {
        Log.d(TAG, "saveBtimapsToEmbedings Start encoding for embedding...")
        val defers = mutableListOf<Deferred<Unit>>()
        it.forEach {
            val defer = async {
                if (it != null) {
                    saveBitmapToEmbedding(
                        it,
                        imageEncoder,
                        embeddingRepository
                    )
                }
            }
            defers.add(defer)
        }
        awaitAll(*defers.toTypedArray())


        Log.d(TAG, "saveBtimapsToEmbedings    Encode[v2] done!")
    }
}
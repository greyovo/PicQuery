package me.grey.picquery.domain

import android.util.Log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope import me.grey.picquery.common.ObjectPool
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.PhotoBitmap
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
                data = feat.array(),
            ))
        }
        Log.d(TAG, "Encode[v2] done! Time: $time")
    }

    suspend fun saveBitmapsToEmbedding(
        it: List<PhotoBitmap?>,
        embeddingRepository: EmbeddingRepository
    ) {
        coroutineScope {
            Log.d(TAG, "saveBitmapsToEmbeddings Start encoding for embedding...")

            val defers = mutableListOf<Deferred<Unit>>()
            it.forEach {
                val defer = async {
                    if (it != null) {
                        saveBitmapToEmbedding(
                            it,
                            ObjectPool.ImageEncoderPool.acquire(),
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
}
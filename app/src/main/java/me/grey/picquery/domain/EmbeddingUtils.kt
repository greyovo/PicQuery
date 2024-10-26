package me.grey.picquery.domain

import android.util.Log
import com.grey.picquery.library.ImageEncoder

import kotlinx.coroutines.coroutineScope
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.data.model.toByteArray
import kotlin.system.measureTimeMillis

object EmbeddingUtils {

    val TAG = "EmbeddingUtils"
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
                val embeddings = imageEncoder.encodeBatch(items.map { it!!.bitmap },)
                Log.d(TAG, "${System.currentTimeMillis()} end encoding image...")
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
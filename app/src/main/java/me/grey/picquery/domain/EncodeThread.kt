package me.grey.picquery.domain

import android.util.Log
import kotlinx.coroutines.runBlocking
import me.grey.picquery.common.toByteArray
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.domain.encoder.ImageEncoder
import java.nio.FloatBuffer

class EncodeThread(
    private val queue: PreloadPhotosQueue,
    private val imageEncoder: ImageEncoder,
    private val embeddingRepository: EmbeddingRepository,
) : Thread() {
    private companion object {
        const val TAG = "EncodeThread"
    }

    private val embListResult = mutableListOf<Embedding>()

    override fun run() {
        var count = 0
        runBlocking {
            Log.d(TAG, "Start encoding for embedding...")
            while (true) {
                val item = queue.get() ?: break

                val feat: FloatBuffer = imageEncoder.encode(item.bitmap, usePreprocess = false)
                embListResult.add(
                    Embedding(
                        photoId = item.photo.id,
                        albumId = item.photo.albumID,
                        data = feat.toByteArray()
                    )
                )
                count += 1
                if (embListResult.size >= 100) {
                    Log.d(TAG, "Submit embeddings, batch=100, total $count")
                    embeddingRepository.updateAll(embListResult.toList())
                    embListResult.clear()
                }
            }
            if (embListResult.isNotEmpty()) {
                Log.d(TAG, "Submit embeddings, remaining=${embListResult.size}, total $count.")
                embeddingRepository.updateAll(embListResult)
            }
            Log.d(TAG, "Encode[v2] done! real count: $count")
        }
    }
}
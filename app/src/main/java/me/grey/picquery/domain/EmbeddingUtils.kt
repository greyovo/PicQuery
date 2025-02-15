package me.grey.picquery.domain

import kotlinx.coroutines.coroutineScope
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.data.model.toByteArray
import me.grey.picquery.feature.base.ImageEncoder
import timber.log.Timber
import java.lang.Float.max
import kotlin.system.measureTimeMillis

object EmbeddingUtils {

    val TAG = "EmbeddingUtils"
    suspend fun saveBitmapsToEmbedding(
        items: List<PhotoBitmap?>,
        imageEncoder: ImageEncoder,
        embeddingRepository: EmbeddingRepository,
        embeddingObjectRepository: ObjectBoxEmbeddingRepository
    ) {
        coroutineScope {
            Timber.tag(TAG).d("saveBitmapsToEmbeddings Start encoding for embedding...")

            Timber.tag(TAG).d("Start encoding for embedding...")
            Timber.tag(TAG).d("${System.currentTimeMillis()} Start encoding image...")
            val time = measureTimeMillis {
                val embeddings = imageEncoder.encodeBatch(items.map { it!!.bitmap })
                Timber.tag(TAG).d("${System.currentTimeMillis()} end encoding image...")
                embeddings.forEachIndexed { index, feat ->

                    embeddingObjectRepository.update(
                        me.grey.picquery.data.model.ObjectBoxEmbedding(
                            photoId = items[index]!!.photo.id,
                            albumId = items[index]!!.photo.albumID,
                            data = feat
                        )
                    )
                }
            }
            val costSec = max(time / 1000f, 0.1f)
            Timber.tag(TAG)
                .d("Encode[v2] done! cost: $costSec s, speed: ${items.size / costSec} pic/s")
        }
    }

}
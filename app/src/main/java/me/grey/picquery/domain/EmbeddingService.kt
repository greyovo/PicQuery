package me.grey.picquery.domain

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.grey.picquery.common.encodeProgressCallback
import me.grey.picquery.common.loadThumbnail
import me.grey.picquery.common.preprocess
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.model.ObjectBoxEmbedding
import me.grey.picquery.data.model.Photo
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.TextEncoder
import timber.log.Timber

/**
 * Encoding Service - Responsible for vector encoding of images and text
 *
 * Responsibilities:
 * - Batch encode photo lists
 * - Encode single image
 * - Encode text
 * - Manage encoding lock
 * - Check if embeddings exist
 */
class EmbeddingService(
    private val context: Context,
    private val imageEncoder: ImageEncoder,
    private val textEncoder: TextEncoder,
    private val embeddingRepository: EmbeddingRepository,
    private val objectBoxEmbeddingRepository: ObjectBoxEmbeddingRepository,
    private val dispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "EmbeddingService"
        private const val BUFFER_SIZE = 1000
        private const val CHUNK_SIZE = 100
    }

    private var encodingLock = false


    /**
     * Check if embeddings exist
     */
    suspend fun hasEmbedding(): Boolean {
        return withContext(dispatcher) {
            val total = embeddingRepository.getTotalCount()
            Timber.tag(TAG).d("Total embedding count $total")
            total > 0
        }
    }

    /**
     * Encode text to vector
     */
    suspend fun encodeText(text: String): FloatArray {
        return withContext(dispatcher) {
            textEncoder.encode(text)
        }
    }

    /**
     * Encode image to vector
     */
    suspend fun encodeBitmap(bitmap: Bitmap): FloatArray {
        return withContext(dispatcher) {
            imageEncoder.encodeBatch(listOf(bitmap))[0]
        }
    }

    /**
     * Batch encode photo list
     *
     * @param photos List of photos to encode
     * @param progressCallback Progress callback
     * @return Whether encoding started successfully (returns false if already encoding)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun encodePhotoList(
        photos: List<Photo>,
        progressCallback: encodeProgressCallback? = null
    ): Boolean {
        if (encodingLock) {
            Timber.tag(TAG).w("encodePhotoList: Already encoding!")
            return false
        }

        Timber.tag(TAG).i("encodePhotoList started with ${photos.size} photos")
        encodingLock = true

        withContext(dispatcher) {
            val cur = AtomicInteger(0)

            photos.asFlow()
                .map { photo -> loadAndPreprocessPhoto(photo) }
                .filterNotNull()
                .buffer(BUFFER_SIZE)
                .chunked(CHUNK_SIZE)
                .onEach { Timber.tag(TAG).d("Processing batch: ${it.size}") }
                .onCompletion {
                    embeddingRepository.updateCache()
                    encodingLock = false
                    Timber.tag(TAG).i("Encoding completed")
                }
                .collect { batch ->
                    val cost = measureTimeMillis {
                        saveBatchToEmbedding(batch)
                    }
                    cur.addAndGet(batch.size)

                    progressCallback?.invoke(
                        cur.get(),
                        photos.size,
                        cost / batch.size
                    )
                    Timber.tag(TAG).d("Batch cost: ${cost}ms")
                }
        }
        return true
    }

    /**
     * Load and preprocess photo
     */
    private suspend fun loadAndPreprocessPhoto(photo: Photo): PhotoBitmap? {
        val thumbnailBitmap = loadThumbnail(context, photo)
        if (thumbnailBitmap == null) {
            Timber.tag(TAG).w("Unsupported file: '${photo.path}', skip encoding")
            return null
        }
        val prepBitmap = preprocess(thumbnailBitmap)
        return PhotoBitmap(photo, prepBitmap)
    }

    /**
     * Batch save embeddings to database
     */
    private suspend fun saveBatchToEmbedding(items: List<PhotoBitmap>) {
        val embeddings = imageEncoder.encodeBatch(items.map { it.bitmap })

        embeddings.forEachIndexed { index, feat ->
            objectBoxEmbeddingRepository.update(
                ObjectBoxEmbedding(
                    photoId = items[index].photo.id,
                    albumId = items[index].photo.albumID,
                    data = feat
                )
            )
        }
    }
}

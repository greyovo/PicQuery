package me.grey.picquery.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.grey.picquery.data.model.PhotoBitmap
import java.util.concurrent.LinkedBlockingQueue

class PreloadPhotosQueue {
    companion object {
        private const val MAX_QUEUE_SIZE = 20
    }

    private val queue = LinkedBlockingQueue<PhotoBitmap>(MAX_QUEUE_SIZE)

    var total = 0L

    val isEmpty get() = queue.isEmpty()

    val size get() = queue.size

    private var preloadCompleted = false

    val isPreloadCompleted get() = preloadCompleted

    fun markPreloadComplete() {
        preloadCompleted = true
    }

    suspend fun put(item: PhotoBitmap) {
        withContext(Dispatchers.IO) {
            queue.put(item)
            total++
        }
    }

    suspend fun get(): PhotoBitmap? {
        return withContext(Dispatchers.IO) {
            if (preloadCompleted && queue.isEmpty()) {
                return@withContext null
            }
            queue.take()
        }
    }
}
package me.grey.picquery.common

import me.grey.picquery.domain.encoder.ImageEncoder
import java.util.concurrent.LinkedBlockingQueue

class ObjectPool<T>(private val factory: () -> T, private val maxSize: Int) {
    private val pool = LinkedBlockingQueue<T>(maxSize)

    init {
        repeat(maxSize) {
            pool.offer(factory())
        }
    }

    fun acquire(): T = pool.poll() ?: factory()

    fun release(obj: T) {
        pool.offer(obj)
    }

    fun clear() {
        while (pool.isNotEmpty()) {
            val imageEncoder =  pool.poll()
            (imageEncoder as? ImageEncoder)?.clearSession()
        }
    }

    companion object {
        private inline fun <reified T> create(maxSize: Int, noinline factory: () -> T) = ObjectPool(factory, maxSize)
        val ImageEncoderPool = create(10) { ImageEncoder() }
    }
}

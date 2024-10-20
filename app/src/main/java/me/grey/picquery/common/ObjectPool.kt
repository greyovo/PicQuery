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

    fun acquire(): T = pool.poll()

    fun release(obj: T) {
        pool.offer(obj)
    }
    companion object {
        inline fun <reified T> create(maxSize: Int, noinline factory: () -> T) = ObjectPool(factory, maxSize)
        val ImageEncoderPool = create(5) { ImageEncoder() }
    }
}

package me.grey.picquery.feature.tf

import androidx.annotation.Keep
import java.io.Closeable
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.TensorFlowLite

/** Uses the existing LiteRT library with an explicitly configured XNNPACK thread pool. */
@Keep
internal class NativeXnnpackDelegate(numThreads: Int) :
    Delegate,
    Closeable {
    private var handle = 0L
    val isThreadPoolActive: Boolean

    init {
        require(numThreads > 0) { "Native XNNPACK thread count must be positive." }
        loadNativeLibrary()
        handle = nativeCreate(numThreads)
        try {
            check(handle != 0L) { "Native XNNPACK delegate creation returned a null handle." }
            isThreadPoolActive = nativeHasThreadPool(handle)
        } catch (failure: Throwable) {
            try {
                close()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    @Synchronized
    override fun getNativeHandle(): Long {
        check(handle != 0L) { "Native XNNPACK delegate is closed." }
        return handle
    }

    @Synchronized
    override fun close() {
        val activeHandle = handle
        handle = 0L
        if (activeHandle != 0L) nativeDelete(activeHandle)
    }

    private external fun nativeCreate(numThreads: Int): Long
    private external fun nativeHasThreadPool(delegateHandle: Long): Boolean
    private external fun nativeDelete(delegateHandle: Long)

    companion object {
        private var loaded = false

        @Synchronized
        private fun loadNativeLibrary() {
            if (loaded) return
            TensorFlowLite.init()
            System.loadLibrary("picquery_xnnpack")
            loaded = true
        }
    }
}

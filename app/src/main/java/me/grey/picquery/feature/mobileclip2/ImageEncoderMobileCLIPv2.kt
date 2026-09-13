package me.grey.picquery.feature.mobileclip2

import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.grey.picquery.feature.base.ImageEncoder

class ImageEncoderMobileCLIPv2(
    private val context: Context,
    private val preprocessor: PreprocessorMobileCLIPv2,
    private val dispatcher: CoroutineDispatcher
) : ImageEncoder {
    private val sessionLock = Any()
    private var session: MobileCLIP2Session? = null
    private var closed = false
    private var inputBuffer: FloatBuffer? = null

    override suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray> = withContext(dispatcher) {
        // Exports deliberately use batch 1; callers can still submit arbitrary batch sizes.
        bitmaps.map { bitmap ->
            currentCoroutineContext().ensureActive()
            synchronized(sessionLock) {
                check(!closed) { "MobileCLIP2 image encoder is closed." }
                val input = inputBuffer ?: preprocessor.allocateBuffer(1).also { inputBuffer = it }
                input.clear()
                preprocessor.preprocessInto(bitmap, input)
                input.flip()
                val activeSession = session ?: MobileCLIP2Session.create(context, MobileCLIP2Tower.IMAGE)
                    .also { session = it }
                activeSession.run(input)
            }
        }
    }

    fun closeSession() = synchronized(sessionLock) {
        closed = true
        val activeSession = session
        session = null
        inputBuffer = null
        activeSession?.close()
    }
}

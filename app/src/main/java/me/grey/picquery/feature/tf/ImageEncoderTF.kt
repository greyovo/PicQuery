package me.grey.picquery.feature.tf

import android.content.Context
import android.graphics.Bitmap
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.Preprocessor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import timber.log.Timber

open class ImageEncoderTF(
    context: Context,
    modelPath: String,
    private val preprocessor: Preprocessor,
    private val dispatcher: CoroutineDispatcher,
    runtimeConfig: TFLiteRuntimeConfig = TFLiteRuntimeConfig.Default
) : ImageEncoder {
    private val session = TFLiteInterpreterSession.fromAsset(
        context = context,
        modelPath = modelPath,
        runtimeConfig = runtimeConfig
    )
    private val interpreter = session.interpreter
    private val outputElementCount = TFLiteTensorShape.outputElementCount(
        interpreter.getOutputTensor(0).shape()
    )
    private val outputBuffer = ThreadLocal.withInitial {
        FloatBuffer.allocate(outputElementCount)
    }

    override suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray> = withContext(dispatcher) {
        bitmaps.map { bitmap ->
            encodeInput(preprocessor.preprocess(bitmap))
        }
    }

    fun closeSession() {
        session.close()
    }

    private fun encodeInput(input: Any): FloatArray {
        val inputBuffer = input.asTFLiteInputBuffer()
        val threadOutputBuffer = outputBuffer.get().apply { clear() }
        synchronized(interpreter) {
            interpreter.run(inputBuffer, threadOutputBuffer)
        }
        threadOutputBuffer.rewind()

        val output = FloatArray(outputElementCount)
        threadOutputBuffer.get(output)
        Timber.tag(TAG).d("Finish encoding image with TF model")
        return output
    }

    private fun Any.asTFLiteInputBuffer(): Buffer = when (this) {
        is TensorBuffer -> buffer.rewound()
        is FloatBuffer -> rewound()
        is ByteBuffer -> rewound()
        else -> throw IllegalArgumentException(
            "Unsupported TF image input buffer: ${this::class.java.name}"
        )
    }

    private fun <T : Buffer> T.rewound(): T {
        rewind()
        return this
    }

    companion object {
        private const val TAG = "ImageEncoderTF"
    }
}

package me.grey.picquery.feature.tf

import android.content.Context
import java.nio.Buffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import me.grey.picquery.feature.BPETokenizer
import me.grey.picquery.feature.base.TextEncoder
import org.tensorflow.lite.DataType
import timber.log.Timber

abstract class TextEncoderTF(
    private val context: Context,
    private val runtimeConfig: TFLiteRuntimeConfig = TFLiteRuntimeConfig.Default
) : TextEncoder {
    abstract val modelPath: String

    private val tokenizer: BPETokenizer by lazy { BPETokenizer(context) }
    private val sessionDelegate = lazy {
        TFLiteInterpreterSession.fromAsset(
            context = context,
            modelPath = modelPath,
            runtimeConfig = runtimeConfig
        )
    }
    private val session: TFLiteInterpreterSession by sessionDelegate
    private val interpreter get() = session.interpreter
    private val inputDataType by lazy {
        interpreter.getInputTensor(0).dataType()
    }
    private val outputDataType by lazy {
        interpreter.getOutputTensor(0).dataType()
    }
    private val outputElementCount by lazy {
        TFLiteTensorShape.outputElementCount(interpreter.getOutputTensor(0).shape())
    }
    private val outputBuffer = ThreadLocal.withInitial {
        FloatBuffer.allocate(outputElementCount)
    }

    init {
        Timber.tag(TAG).d("Init $TAG")
    }

    override fun encode(input: String): FloatArray {
        val tokenIds = tokenizer.tokenize(input).first
        val inputBuffer = tokenIds.toInputBuffer(inputDataType)
        require(outputDataType == DataType.FLOAT32) {
            "Unsupported TF text output type: $outputDataType"
        }

        val threadOutputBuffer = outputBuffer.get().apply { clear() }
        synchronized(interpreter) {
            interpreter.run(inputBuffer, threadOutputBuffer)
        }
        threadOutputBuffer.rewind()

        val output = FloatArray(outputElementCount)
        threadOutputBuffer.get(output)
        return output
    }

    fun closeSession() {
        if (sessionDelegate.isInitialized()) {
            session.close()
        }
    }

    private fun IntArray.toInputBuffer(dataType: DataType): Buffer = when (dataType) {
        DataType.INT32 -> IntBuffer.wrap(this)
        DataType.INT64 -> LongBuffer.wrap(LongArray(size) { index -> this[index].toLong() })
        else -> throw IllegalArgumentException("Unsupported TF text input type: $dataType")
    }.apply { rewind() }

    companion object {
        private const val TAG = "TextEncoderTF"
    }
}

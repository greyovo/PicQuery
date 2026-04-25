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
    useGpuDelegate: Boolean = true
) : TextEncoder {
    abstract val modelPath: String

    private val tokenizer: BPETokenizer by lazy { BPETokenizer(context) }
    private val sessionDelegate = lazy {
        TFLiteInterpreterSession.fromAsset(
            context = context,
            modelPath = modelPath,
            useGpuDelegate = useGpuDelegate
        )
    }
    private val session: TFLiteInterpreterSession by sessionDelegate
    private val interpreter get() = session.interpreter

    init {
        Timber.tag(TAG).d("Init $TAG")
    }

    override fun encode(input: String): FloatArray {
        val tokenIds = tokenizer.tokenize(input).first
        val inputBuffer = tokenIds.toInputBuffer(interpreter.getInputTensor(0).dataType())
        val outputTensor = interpreter.getOutputTensor(0)
        require(outputTensor.dataType() == DataType.FLOAT32) {
            "Unsupported TF text output type: ${outputTensor.dataType()}"
        }

        val outputBuffer = FloatBuffer.allocate(
            TFLiteTensorShape.outputElementCount(outputTensor.shape())
        )
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val output = FloatArray(outputBuffer.capacity())
        outputBuffer.get(output)
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

package me.grey.picquery.feature.mobileclip2

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import java.io.Closeable
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt
import me.grey.picquery.BuildConfig
import me.grey.picquery.common.AssetUtil
import me.grey.picquery.feature.tf.TFLiteInterpreterSession
import me.grey.picquery.feature.tf.TFLiteRuntimeConfig
import org.tensorflow.lite.DataType

internal enum class MobileCLIP2Tower(val onnxAsset: String, val tfliteAsset: String, val inputShape: LongArray) {
    IMAGE("mobileclip2_s0_image.onnx", "image_model.tflite", longArrayOf(1, 3, 256, 256)),
    TEXT("mobileclip2_s0_text_int8.onnx", "text_model_dynamic_wi8.tflite", longArrayOf(1, 77))
}

/** Owned by an encoder; creation, inference and disposal happen under its session lock. */
internal interface MobileCLIP2Session : Closeable {
    fun run(input: Buffer): FloatArray
    val nativeXnnpackThreadPoolActive: Boolean get() = false

    companion object {
        const val FEATURE_SIZE = 512
        const val NUM_THREADS = 4

        fun numThreads(tower: MobileCLIP2Tower): Int = if (BuildConfig.MOBILECLIP2_BACKEND == "tflite" && tower == MobileCLIP2Tower.TEXT) 2 else NUM_THREADS

        fun create(context: Context, tower: MobileCLIP2Tower): MobileCLIP2Session = when (BuildConfig.MOBILECLIP2_BACKEND) {
            "onnx" -> OnnxMobileCLIP2Session(context, tower)
            "tflite" -> TFLiteMobileCLIP2Session(context, tower)
            else -> error("Unknown MobileCLIP2 backend: ${BuildConfig.MOBILECLIP2_BACKEND}")
        }
    }
}

private class OnnxMobileCLIP2Session(context: Context, private val tower: MobileCLIP2Tower) : MobileCLIP2Session {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(MobileCLIP2Session.numThreads(tower))
        options.setInterOpNumThreads(1)
        environment.createSession(AssetUtil.assetFilePath(context, tower.onnxAsset), options)
    }
    private val inputName: String
    private val inputInfo: TensorInfo

    init {
        try {
            require(session.numInputs == 1L && session.numOutputs == 1L) {
                "${tower.onnxAsset} must have one input and one output."
            }
            inputName = session.inputNames.single()
            inputInfo = session.inputInfo.getValue(inputName).info as? TensorInfo
                ?: error("${tower.onnxAsset} input must be a tensor.")
            require(inputInfo.shape.contentEquals(tower.inputShape)) {
                "${tower.onnxAsset} input shape: ${inputInfo.shape.contentToString()}"
            }
            val validInputType = when (tower) {
                MobileCLIP2Tower.IMAGE -> inputInfo.type == OnnxJavaType.FLOAT
                MobileCLIP2Tower.TEXT -> inputInfo.type == OnnxJavaType.INT32 || inputInfo.type == OnnxJavaType.INT64
            }
            require(validInputType) { "${tower.onnxAsset} input type: ${inputInfo.type}" }
            val outputInfo = session.outputInfo.values.single().info as? TensorInfo
                ?: error("${tower.onnxAsset} output must be a tensor.")
            require(outputInfo.type == OnnxJavaType.FLOAT && outputInfo.shape.contentEquals(longArrayOf(1, 512))) {
                "${tower.onnxAsset} output must be float32[1, 512]."
            }
        } catch (error: Throwable) {
            session.close()
            throw error
        }
    }

    override fun run(input: Buffer): FloatArray {
        input.rewind()
        val tensor = when (inputInfo.type) {
            OnnxJavaType.FLOAT -> OnnxTensor.createTensor(environment, input as FloatBuffer, tower.inputShape)

            OnnxJavaType.INT32 -> OnnxTensor.createTensor(environment, input as IntBuffer, tower.inputShape)

            OnnxJavaType.INT64 -> {
                val tokens = input as IntBuffer
                val longs = LongBuffer.wrap(LongArray(tokens.remaining()) { tokens.get().toLong() })
                OnnxTensor.createTensor(environment, longs, tower.inputShape)
            }

            else -> error("Unsupported MobileCLIP2 input type: ${inputInfo.type}")
        }
        tensor.use {
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = result.get(0) as OnnxTensor
                val buffer = output.floatBuffer
                require(buffer.remaining() == MobileCLIP2Session.FEATURE_SIZE) { "Expected a 512-element embedding." }
                return normalizeFeature(FloatArray(buffer.remaining()).also { buffer.get(it) })
            }
        }
    }

    override fun close() = session.close()
}

private class TFLiteMobileCLIP2Session(context: Context, tower: MobileCLIP2Tower) : MobileCLIP2Session {
    private val session = TFLiteInterpreterSession.fromAsset(
        context,
        tower.tfliteAsset,
        TFLiteRuntimeConfig(
            useGpuDelegate = false,
            numThreads = MobileCLIP2Session.numThreads(tower),
            useNativeXnnpack = true
        )
    )
    private val interpreter = session.interpreter
    override val nativeXnnpackThreadPoolActive: Boolean get() = session.isNativeXnnpackThreadPoolActive
    private val outputBuffer = ByteBuffer.allocateDirect(MobileCLIP2Session.FEATURE_SIZE * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    init {
        try {
            require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1) {
                "${tower.tfliteAsset} must have one input and one output."
            }
            val input = interpreter.getInputTensor(0)
            val expectedType = if (tower == MobileCLIP2Tower.IMAGE) DataType.FLOAT32 else DataType.INT32
            val expectedShape = tower.inputShape.map { it.toInt() }.toIntArray()
            require(input.dataType() == expectedType && input.shape().contentEquals(expectedShape)) {
                "${tower.tfliteAsset} input must be $expectedType${tower.inputShape.contentToString()}."
            }
            val output = interpreter.getOutputTensor(0)
            require(output.dataType() == DataType.FLOAT32 && output.shape().contentEquals(intArrayOf(1, 512))) {
                "${tower.tfliteAsset} output must be float32[1, 512]."
            }
        } catch (error: Throwable) {
            session.close()
            throw error
        }
    }

    override fun run(input: Buffer): FloatArray {
        input.rewind()
        outputBuffer.clear()
        interpreter.run(input, outputBuffer)
        outputBuffer.rewind()
        return normalizeFeature(FloatArray(MobileCLIP2Session.FEATURE_SIZE).also { outputBuffer.get(it) })
    }

    override fun close() = session.close()
}

private fun normalizeFeature(feature: FloatArray): FloatArray {
    require(feature.size == MobileCLIP2Session.FEATURE_SIZE && feature.all { it.isFinite() }) {
        "MobileCLIP2 must produce 512 finite values."
    }
    val norm = sqrt(feature.sumOf { it.toDouble() * it.toDouble() })
    require(norm > 0.0) { "MobileCLIP2 produced a zero embedding." }
    for (index in feature.indices) feature[index] = (feature[index] / norm).toFloat()
    return feature
}

package me.grey.picquery.feature

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.IntBuffer
import java.nio.LongBuffer
import me.grey.picquery.common.AssetUtil
import me.grey.picquery.feature.base.TextEncoder

abstract class TextEncoderONNX(private val context: Context) : TextEncoder {
    private val TAG = this.javaClass.simpleName
    abstract val modelPath: String
    abstract val modelType: Int
    private var ortSession: OrtSession? = null
    private var tokenizer: BPETokenizer? = null

    private var options = OrtSession.SessionOptions().apply {
        addConfigEntry("session.load_model_format", "ORT")
    }

    init {
        Log.d(TAG, "Init $TAG")
    }

    override fun encode(input: String): FloatArray {
        if (tokenizer == null) {
            tokenizer = BPETokenizer(context)
        }
        val token = tokenizer!!.tokenize(input)
        val intBuffer = IntBuffer.wrap(token.first)
        val shape = token.second

        val ortEnv = OrtEnvironment.getEnvironment()
        if (ortSession == null) {
            ortSession = ortEnv.createSession(AssetUtil.assetFilePath(context, modelPath), options)
        }

        val inputName = ortSession?.inputNames?.iterator()?.next()
        ortEnv.use { env ->

            val tensor = when (modelType) {
                0 -> OnnxTensor.createTensor(env, intBuffer, shape)
                1 -> {
                    val longBuffer = LongBuffer.allocate(intBuffer.capacity()).apply {
                        while (intBuffer.hasRemaining()) {
                            put(intBuffer.get().toLong())
                        }
                        flip()
                    }
                    OnnxTensor.createTensor(env, longBuffer, shape)
                }

                else -> throw IllegalArgumentException("Unknown buffer type")
            }
            val output = ortSession?.run(mapOf(Pair(inputName!!, tensor)))
            val resultBuffer = output?.get(0) as OnnxTensor
            return (resultBuffer.floatBuffer).array()
        }
    }
}

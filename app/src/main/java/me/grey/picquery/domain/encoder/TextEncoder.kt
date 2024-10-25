package me.grey.picquery.domain.encoder

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.common.AssetUtil
import java.nio.IntBuffer
import java.nio.LongBuffer


class TextEncoder {
    companion object {
        private const val TAG = "TextEncoder"
        private const val modelPath = "text_model.ort"
    }

    private var ortSession: OrtSession? = null
    private var tokenizer: BPETokenizer? = null

    private var options = OrtSession.SessionOptions().apply {
        addConfigEntry("session.load_model_format", "ORT")
    }

    init {
        Log.d(TAG, "Init $TAG")
    }

    fun encode(input: String): FloatArray {
        if (tokenizer == null) {
            tokenizer = BPETokenizer(context)
        }
        val token = tokenizer!!.tokenize(input)
        val intBuffer = IntBuffer.wrap(token.first)
        val longBuffer = LongBuffer.allocate(intBuffer.capacity())
        while (intBuffer.hasRemaining()) {
            longBuffer.put(intBuffer.get().toLong())
        }
        longBuffer.flip()
        val shape = token.second

        val ortEnv = OrtEnvironment.getEnvironment()
        if (ortSession == null) {
            ortSession = ortEnv.createSession(AssetUtil.assetFilePath(context, modelPath), options)
        }

        val inputName = ortSession?.inputNames?.iterator()?.next()
        ortEnv.use { env ->
            val tensor = OnnxTensor.createTensor(env, longBuffer, shape)
            val output = ortSession?.run(mapOf(Pair(inputName!!, tensor)))
            val resultBuffer = output?.get(0) as OnnxTensor
            return (resultBuffer.floatBuffer).array()
        }
    }

}
package me.grey.picquery.core.encoder

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import android.util.Log
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.common.assetFilePath
import java.nio.IntBuffer


object TextEncoder {
    private const val modelPath = "clip-text-encoder-quant-int8.with_runtime_opt.ort"
//        private const val modelPath = "clip-text-encoder-quant-int8.onnx"
//    private const val modelPath = "clip-text-encoder.onnx"

    private var ortSession: OrtSession? = null
    private var tokenizer: BPETokenizer? = null

    init {
        val ortEnv = OrtEnvironment.getEnvironment()
        val session_options = SessionOptions()
        session_options.addConfigEntry("session.load_model_format", "ORT")
        ortSession = ortEnv?.createSession(assetFilePath(context, modelPath), session_options)
    }

    fun encode(input: String): FloatArray {
        if (tokenizer == null) {
            tokenizer = BPETokenizer(context)
        }
        val token = tokenizer!!.tokenize(input)
        Log.d("tokenizer", token.first.size.toString())
        val buffer = IntBuffer.wrap(token.first)
        val shape = token.second
        val inputName = ortSession?.inputNames?.iterator()?.next()
        Log.d("inputName", inputName ?: "null")
        val env = OrtEnvironment.getEnvironment()
        val tensor = OnnxTensor.createTensor(env, buffer, shape)
        Log.d("inputTensor", tensor.intBuffer.array().joinToString())
        Log.d("OutputName", ortSession!!.outputNames.joinToString())
        val output = ortSession?.run(mapOf(Pair(inputName!!, tensor)))

        val resultBuffer = output?.get(0) as OnnxTensor
        return (resultBuffer.floatBuffer).array()
    }

}
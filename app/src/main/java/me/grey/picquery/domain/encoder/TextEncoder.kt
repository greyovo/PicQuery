package me.grey.picquery.domain.encoder

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import android.util.Log
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.common.assetFilePath
import me.grey.picquery.domain.tokenizer.BertTokenizer
import me.grey.picquery.domain.tokenizer.loadVocab
import java.nio.LongBuffer


class TextEncoder {
    companion object {
        //        private const val modelPath = "clip-text-encoder-quant-int8.with_runtime_opt.ort"
        private const val modelPath = "clip-cn-text-encoder-quant-int8.with_runtime_opt.ort"
        private const val vocabPath = "cn_clip_vocab"
        private const val TAG = "TextEncoder"
    }
//        private const val modelPath = "clip-text-encoder-quant-int8.onnx"
//    private const val modelPath = "clip-text-encoder.onnx"

    private var ortSession: OrtSession? = null

    //        private var tokenizer: BPETokenizer? = null
    private var tokenizer: BertTokenizer? = null

    init {
        val ortEnv = OrtEnvironment.getEnvironment()
        val options = SessionOptions()
        options.addConfigEntry("session.load_model_format", "ORT")
        ortSession = ortEnv?.createSession(assetFilePath(context, modelPath), options)
    }

    fun encode(input: String): FloatArray {
        if (tokenizer == null) {
//            tokenizer = BPETokenizer(context)
            tokenizer = BertTokenizer { loadVocab(context, vocabPath) }
        }
        val token = tokenizer!!.tokenize(input)
        val buffer = LongBuffer.wrap(token.toLongArray())
        val shape = BertTokenizer.shape
        val inputName = ortSession?.inputNames?.iterator()?.next()
        val env = OrtEnvironment.getEnvironment()
        val tensor = OnnxTensor.createTensor(env, buffer, shape)
        val output = ortSession?.run(mapOf(Pair(inputName!!, tensor)))

        val resultBuffer = output?.get(0) as OnnxTensor
        Log.d(TAG, (resultBuffer.floatBuffer).array().joinToString())

        return (resultBuffer.floatBuffer).array()
    }
}

private fun Collection<Int>.toLongArray(): LongArray {
    val result = LongArray(size)
    var index = 0
    for (element in this)
        result[index++] = element.toLong()
    return result
}
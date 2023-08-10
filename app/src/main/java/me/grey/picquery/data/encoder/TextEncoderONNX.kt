package me.grey.picquery.data.encoder

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import me.grey.picquery.util.assetFilePath
import org.pytorch.*
import java.nio.IntBuffer
import java.util.*

class TextEncoderONNX(private val context: Context) {
    private val modelPath = "clip-text-encoder-quant-int8.onnx"

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: BPETokenizer? = null

    init {
        ortEnv = OrtEnvironment.getEnvironment()
        ortSession = ortEnv?.createSession(assetFilePath(context, modelPath))
    }

    fun encode(input: String): Array<FloatArray> {
        if (tokenizer == null) {
            tokenizer = BPETokenizer(context)
        }
        val token = tokenizer!!.tokenize(input)
        val buffer = IntBuffer.wrap(token.first)
        val shape = token.second
        val inputName = ortSession?.inputNames?.iterator()?.next()

        val env = OrtEnvironment.getEnvironment()
        env.use {
            val tensor = OnnxTensor.createTensor(env, buffer, shape)
            tensor.use {
                val output = ortSession?.run(Collections.singletonMap(inputName, tensor))
                output.use {
                    @Suppress("UNCHECKED_CAST")
                    return (output?.get(0)?.value) as Array<FloatArray>
                }
            }
        }
    }

}
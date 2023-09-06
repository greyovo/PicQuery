package me.grey.picquery.core.encoder

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.common.assetFilePath
import org.pytorch.*
import java.nio.IntBuffer
import java.util.*

object TextEncoder {
    private const val modelPath = "clip-text-encoder-quant-int8.onnx"

    private var ortSession: OrtSession? = null
    private var tokenizer: BPETokenizer? = null

    init {
        val ortEnv = OrtEnvironment.getEnvironment()
        ortSession = ortEnv?.createSession(assetFilePath(context, modelPath))
    }

    fun encode(input: String): FloatArray {
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
                    val resultBuffer = output?.get(0) as OnnxTensor
                    @Suppress("UNCHECKED_CAST")
                    return (resultBuffer.floatBuffer).array()
                }
//                output.use {
//                    @Suppress("UNCHECKED_CAST")
//                    return (output?.get(0)?.value) as Array<FloatArray>
//                }
            }
        }
    }

}
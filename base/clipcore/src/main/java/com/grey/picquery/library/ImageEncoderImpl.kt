package com.grey.picquery.library

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.grey.picQuery.core.AssetUtil
import com.grey.picQuery.core.defaultDispatcher
import kotlinx.coroutines.withContext
import java.util.Collections

open class ImageEncoderImpl(val DIM: Long,val modelPath: String,context: Context, private val embeddingMaker: EmbeddingMaker): ImageEncoder {

    private val TAG = this::class.java.simpleName

    var ortSession: OrtSession? = null
    val ortEnv = OrtEnvironment.getEnvironment()
    private var options = OrtSession.SessionOptions().apply {
        addConfigEntry("session.load_model_format", "ORT")
    }

    init {
        ortSession = ortEnv.createSession(
            AssetUtil.assetFilePath(context, modelPath),
            options
        )
    }

    fun clearSession() {
        ortSession?.close()
        ortSession = null
    }

    init {
        Log.d(TAG, "Init $TAG")
    }

    override suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray> =
        withContext(defaultDispatcher) {
            Log.d(TAG, "${this@ImageEncoderImpl} Start encoding image...")

            val floatBuffer = embeddingMaker.makeBatchEmbedding(bitmaps)

            val inputName = ortSession?.inputNames?.iterator()?.next()
            val shape: LongArray = longArrayOf(bitmaps.size.toLong(), 3, DIM.toLong(), DIM.toLong())
            ortEnv.use { env ->
                val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)
                val output: OrtSession.Result? =
                    ortSession?.run(Collections.singletonMap(inputName, tensor))
                val resultBuffer = output?.get(0) as OnnxTensor
                Log.d(TAG, "Finish encoding image!")

                val feat = resultBuffer.floatBuffer
                val embeddingSize = 512
                val numEmbeddings = feat.capacity() / embeddingSize
                val embeddings = mutableListOf<FloatArray>()

                for (i in 0 until numEmbeddings) {
                    val start = i * embeddingSize
                    val embeddingArray = FloatArray(embeddingSize)
                    feat.position(start)
                    feat.get(embeddingArray, 0, embeddingSize)
                    embeddings.add(embeddingArray)
                }

                return@withContext embeddings
            }
        }
}
package me.grey.picquery.feature

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.withContext
import me.grey.picquery.common.AssetUtil
import me.grey.picquery.common.defaultDispatcher
import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.Preprocessor
import java.nio.FloatBuffer
import java.util.Collections

open class ImageEncoderONNX(
    val DIM: Long,
    val modelPath: String,
    context: Context,
    private val preprocessor: Preprocessor,
) :
    ImageEncoder {

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
            Log.d(TAG, "${this@ImageEncoderONNX} Start encoding image...")

            val floatBuffer = preprocessor.preprocessBatch(bitmaps) as FloatBuffer

            val inputName = ortSession?.inputNames?.iterator()?.next()
            val shape: LongArray = longArrayOf(bitmaps.size.toLong(), 3, DIM, DIM)
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
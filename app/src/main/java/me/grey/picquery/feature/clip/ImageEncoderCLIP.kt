package me.grey.picquery.feature.clip

import ai.onnxruntime.OnnxTensor
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import me.grey.picquery.feature.ImageEncoderONNX
import java.nio.FloatBuffer
import java.util.Collections

class ImageEncoderCLIP(context: Context, private val preprocessor: PreprocessorCLIP, private val dispatcher: CoroutineDispatcher) :
    ImageEncoderONNX(
        224, "clip-image-int8.ort", context, preprocessor, dispatcher
    ) {

    companion object {
        const val INPUT = 224
    }

    override suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray> {
        val inputName = ortSession?.inputNames?.iterator()?.next()

        ortEnv.use {
            val floatBuffer = (preprocessor.preprocessBatch(bitmaps)).array()!!
            val buffers = splitFloatBuffer(FloatBuffer.wrap(floatBuffer), bitmaps.size)

            // Correct shape calculation
            val shape: LongArray = longArrayOf(1, 3, INPUT.toLong(), INPUT.toLong())
            val res = mutableListOf<FloatArray>()
            for (i in bitmaps.indices) {
                val tensor = OnnxTensor.createTensor(ortEnv, buffers[i], shape)
                val output = ortSession?.run(Collections.singletonMap(inputName, tensor))
                @Suppress("UNCHECKED_CAST") val rawOutput =
                    ((output?.get(0)?.value) as Array<FloatArray>)[0]
                res.add(rawOutput)
            }

            return res
        }
    }

    private fun splitFloatBuffer(buffer: FloatBuffer, parts: Int): List<FloatBuffer> {
        val totalSize = buffer.capacity()
        val partSize = totalSize / parts
        val result = mutableListOf<FloatBuffer>()

        for (i in 0 until parts) {
            val start = i * partSize
            val end = if (i == parts - 1) totalSize else start + partSize
            val partBuffer = FloatBuffer.allocate(end - start)
            for (j in start until end) {
                partBuffer.put(buffer[j])
            }
            partBuffer.flip()
            result.add(partBuffer)
        }

        return result
    }
}

package com.grey.clip.imageEncoder

import ai.onnxruntime.OnnxTensor
import android.content.Context
import android.graphics.Bitmap
import com.grey.picquery.library.EmbeddingMaker
import com.grey.picquery.library.ImageEncoderImpl
import java.nio.FloatBuffer
import java.util.Collections

const val INPUT = 224

class ClipImageEncoder(context: Context, private val embeddingMaker: EmbeddingMaker) :
    ImageEncoderImpl(
        INPUT.toLong(), "clip-image-int8.ort", context, embeddingMaker
    ) {
    override suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray> {
        val inputName = ortSession?.inputNames?.iterator()?.next()

        ortEnv.use {
            val floatBuffer = embeddingMaker.makeBatchEmbedding(bitmaps).array()!!
            val buffers = splitFloatBuffer(FloatBuffer.wrap(floatBuffer), bitmaps.size)

            // Correct shape calculation
            val shape: LongArray = longArrayOf(1, 3, INPUT.toLong(), INPUT.toLong())
            val res = mutableListOf<FloatArray>()
            for (i in 0 until bitmaps.size) {
                val tensor = OnnxTensor.createTensor(ortEnv, buffers[i], shape)
                val output = ortSession?.run(Collections.singletonMap(inputName, tensor))
                @Suppress("UNCHECKED_CAST") val rawOutput =
                    ((output?.get(0)?.value) as Array<FloatArray>)[0]
                res.add(rawOutput)
            }

            return res
        }
    }

    fun splitFloatBuffer(buffer: FloatBuffer, parts: Int): List<FloatBuffer> {
        val totalSize = buffer.capacity()
        val partSize = totalSize / parts
        val result = mutableListOf<FloatBuffer>()

        for (i in 0 until parts) {
            val start = i * partSize
            val end = if (i == parts - 1) totalSize else start + partSize
            val partBuffer = FloatBuffer.allocate(end - start)
            for (j in start until end) {
                partBuffer.put(buffer.get(j))
            }
            partBuffer.flip()
            result.add(partBuffer)
        }

        return result
    }
}

class ClipEmbeddingMaker() : EmbeddingMaker {

    val DIM_BATCH_SIZE = 1
    val DIM_PIXEL_SIZE = 3

    override suspend fun makeBatchEmbedding(input: List<Bitmap>): FloatBuffer {
        return bitmapsToFloatBuffer(input)
    }

    private val normMeanRGB = floatArrayOf(0.48145467f, 0.4578275f, 0.40821072f)
    private val normStdRGB = floatArrayOf(0.26862955f, 0.2613026f, 0.2757771f)

    fun bitmapToFloatBuffer(bm: Bitmap): FloatBuffer {
        val bitmap = Bitmap.createScaledBitmap(bm, INPUT, INPUT, true)
        val imgData = FloatBuffer.allocate(
            DIM_BATCH_SIZE * DIM_PIXEL_SIZE * INPUT * INPUT
        )
        imgData.rewind()
        val stride = INPUT * INPUT
        val bmpData = IntArray(stride)
        bitmap.getPixels(bmpData, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in 0 until INPUT) {
            for (j in 0 until INPUT) {
                val idx = INPUT * i + j
                val pixelValue = bmpData[idx]
                imgData.put(idx, (((pixelValue shr 16 and 0xFF) / 255f - normMeanRGB[0]) / normStdRGB[0]))
                imgData.put(
                    idx + stride, (((pixelValue shr 8 and 0xFF) / 255f - normMeanRGB[1]) / normStdRGB[1])
                )
                imgData.put(
                    idx + stride * 2, (((pixelValue and 0xFF) / 255f - normMeanRGB[2]) / normStdRGB[2])
                )
            }
        }

        imgData.rewind()
        return imgData
    }

    fun bitmapsToFloatBuffer(bitmaps: List<Bitmap>): FloatBuffer {
        val totalSize = bitmaps.size * 3 * INPUT * INPUT
        val combinedBuffer = FloatBuffer.allocate(totalSize)

        for (bitmap in bitmaps) {
            val floatBuffer = bitmapToFloatBuffer(bitmap)
            combinedBuffer.put(floatBuffer)
        }

        combinedBuffer.flip()
        return combinedBuffer
    }
}
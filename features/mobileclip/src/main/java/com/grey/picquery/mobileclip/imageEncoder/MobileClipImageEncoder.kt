package com.grey.picquery.mobileclip.imageEncoder

import android.content.Context
import android.graphics.Bitmap
import com.grey.picquery.library.EmbeddingMaker
import com.grey.picquery.library.ImageEncoderImpl
import java.nio.FloatBuffer

const val INPUT = 256

class MobileClipImageEncoder(context: Context, mobileClipEmbeddingMaker: EmbeddingMaker) :
    ImageEncoderImpl(
        INPUT.toLong(), "vision_model.ort", context, mobileClipEmbeddingMaker
    )

class MobileClipEmbeddingMaker() : EmbeddingMaker {

    val DIM_BATCH_SIZE = 1
    val DIM_PIXEL_SIZE = 3

    override suspend fun makeBatchEmbedding(input: List<Bitmap>): FloatBuffer {
        return bitmapsToFloatBuffer(input)
    }

    /**
     * to be used by mobile clip
     */
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
                imgData.put(idx, (((pixelValue shr 16 and 0xFF) / 255f)))
                imgData.put(
                    idx + stride, (((pixelValue shr 8 and 0xFF) / 255f))
                )
                imgData.put(
                    idx + stride * 2, (((pixelValue and 0xFF) / 255f))
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
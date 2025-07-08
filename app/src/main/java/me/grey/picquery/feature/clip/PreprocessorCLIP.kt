package me.grey.picquery.feature.clip

import android.graphics.Bitmap
import java.nio.FloatBuffer
import me.grey.picquery.feature.base.Preprocessor

class PreprocessorCLIP : Preprocessor {

    val DIM_BATCH_SIZE = 1
    val DIM_PIXEL_SIZE = 3

    companion object {
        const val INPUT = 224
    }

    override suspend fun preprocessBatch(input: List<Bitmap>): FloatBuffer {
        return bitmapsToFloatBuffer(input)
    }

    override suspend fun preprocess(input: Bitmap): FloatBuffer {
        return bitmapToFloatBuffer(input)
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
                imgData.put(
                    idx,
                    (((pixelValue shr 16 and 0xFF) / 255f - normMeanRGB[0]) / normStdRGB[0])
                )
                imgData.put(
                    idx + stride,
                    (((pixelValue shr 8 and 0xFF) / 255f - normMeanRGB[1]) / normStdRGB[1])
                )
                imgData.put(
                    idx + stride * 2,
                    (((pixelValue and 0xFF) / 255f - normMeanRGB[2]) / normStdRGB[2])
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

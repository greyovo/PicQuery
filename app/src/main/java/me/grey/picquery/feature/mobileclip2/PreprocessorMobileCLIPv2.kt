package me.grey.picquery.feature.mobileclip2

import android.graphics.Bitmap
import java.nio.FloatBuffer
import me.grey.picquery.feature.base.Preprocessor

class PreprocessorMobileCLIPv2 : Preprocessor {
    companion object {
        const val IMAGE_SIZE = 256
        private const val DIM_BATCH_SIZE = 1
        private const val DIM_PIXEL_SIZE = 3
    }

    override suspend fun preprocessBatch(input: List<Bitmap>): FloatBuffer {
        val combinedBuffer = FloatBuffer.allocate(
            input.size * DIM_PIXEL_SIZE * IMAGE_SIZE * IMAGE_SIZE
        )
        for (bitmap in input) {
            combinedBuffer.put(preprocess(bitmap))
        }
        combinedBuffer.flip()
        return combinedBuffer
    }

    override suspend fun preprocess(input: Bitmap): FloatBuffer {
        val bitmap = Bitmap.createScaledBitmap(input, IMAGE_SIZE, IMAGE_SIZE, true)
        val imageData = FloatBuffer.allocate(
            DIM_BATCH_SIZE * DIM_PIXEL_SIZE * IMAGE_SIZE * IMAGE_SIZE
        )
        imageData.rewind()

        val stride = IMAGE_SIZE * IMAGE_SIZE
        val bitmapData = IntArray(stride)
        bitmap.getPixels(bitmapData, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val index = IMAGE_SIZE * i + j
                val pixelValue = bitmapData[index]
                imageData.put(index, (pixelValue shr 16 and 0xFF) / 255f)
                imageData.put(index + stride, (pixelValue shr 8 and 0xFF) / 255f)
                imageData.put(index + stride * 2, (pixelValue and 0xFF) / 255f)
            }
        }

        imageData.rewind()
        return imageData
    }
}

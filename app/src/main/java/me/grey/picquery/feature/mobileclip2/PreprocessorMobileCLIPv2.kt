package me.grey.picquery.feature.mobileclip2

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.round
import me.grey.picquery.feature.base.Preprocessor

class PreprocessorMobileCLIPv2 : Preprocessor {
    companion object {
        const val IMAGE_SIZE = 256
        private const val DIM_PIXEL_SIZE = 3
        private const val PIXELS = IMAGE_SIZE * IMAGE_SIZE
    }

    private val scratchLock = Any()
    private val pixels by lazy { IntArray(PIXELS) }
    private val channels by lazy { FloatArray(PIXELS * DIM_PIXEL_SIZE) }

    override suspend fun preprocessBatch(input: List<Bitmap>): FloatBuffer {
        val combinedBuffer = allocateBuffer(input.size)
        for (bitmap in input) {
            preprocessInto(bitmap, combinedBuffer)
        }
        combinedBuffer.flip()
        return combinedBuffer
    }

    override suspend fun preprocess(input: Bitmap): FloatBuffer = allocateBuffer(1).apply {
        preprocessInto(input, this)
        flip()
    }

    /** Appends one image; scratch memory is private and returned buffers never alias it. */
    internal fun preprocessInto(input: Bitmap, output: FloatBuffer) = synchronized(scratchLock) {
        require(output.remaining() >= PIXELS * DIM_PIXEL_SIZE) { "Input buffer has insufficient space." }
        // OpenCLIP MobileCLIP2-S0/dfndr2b: shortest-side bilinear resize, center crop,
        // RGB in [0, 1], NCHW. Android filtering can differ from Pillow's antialiasing.
        val width = input.width
        val height = input.height
        val resizedWidth = if (width <= height) IMAGE_SIZE else (width.toLong() * IMAGE_SIZE / height).toInt()
        val resizedHeight = if (height <= width) IMAGE_SIZE else (height.toLong() * IMAGE_SIZE / width).toInt()
        val softwareInput = if (input.config == Bitmap.Config.HARDWARE) {
            checkNotNull(input.copy(Bitmap.Config.ARGB_8888, false)) { "Cannot read hardware bitmap." }
        } else {
            input
        }
        var resized: Bitmap? = null
        try {
            val bitmap = Bitmap.createScaledBitmap(softwareInput, resizedWidth, resizedHeight, true)
            resized = bitmap
            // Python round() used by torchvision CenterCrop rounds .5 to the even integer.
            val left = round((resizedWidth - IMAGE_SIZE) / 2.0).toInt()
            val top = round((resizedHeight - IMAGE_SIZE) / 2.0).toInt()
            val pixelData = pixels
            val channelData = channels
            bitmap.getPixels(pixelData, 0, IMAGE_SIZE, left, top, IMAGE_SIZE, IMAGE_SIZE)
            for (index in pixelData.indices) {
                val pixel = pixelData[index]
                channelData[index] = (pixel shr 16 and 0xFF) / 255f
                channelData[index + PIXELS] = (pixel shr 8 and 0xFF) / 255f
                channelData[index + PIXELS * 2] = (pixel and 0xFF) / 255f
            }
            output.put(channelData)
        } finally {
            if (resized !== softwareInput) resized?.recycle()
            if (softwareInput !== input) softwareInput.recycle()
        }
    }

    internal fun allocateBuffer(batchSize: Int): FloatBuffer {
        require(batchSize >= 0) { "Batch size must be non-negative." }
        val byteCount = Math.multiplyExact(batchSize, DIM_PIXEL_SIZE * PIXELS * Float.SIZE_BYTES)
        return ByteBuffer.allocateDirect(byteCount)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }
}

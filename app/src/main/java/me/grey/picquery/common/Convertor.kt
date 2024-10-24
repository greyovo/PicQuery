package me.grey.picquery.common

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

enum class MemoryFormat(val jniCode: Int) {
    CONTIGUOUS(1), CHANNELS_LAST(2), CHANNELS_LAST_3D(3)
}

/**
 * Copy from [org.pytorch.torchvision.TensorImageUtils]
 * under: https://github.com/pytorch/vision/blob/main/LICENSE
 */
fun allocateFloatBuffer(numElements: Int): FloatBuffer {
    return ByteBuffer.allocateDirect(numElements * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
}

fun bitmapToFloatBuffer(
    bitmap: Bitmap,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    normMeanRGB: FloatArray,
    normStdRGB: FloatArray,
    outBuffer: FloatBuffer,
    outBufferOffset: Int,
    memoryFormat: MemoryFormat
) {
    fun checkNormMeanArg(normMeanRGB: FloatArray) {
        require(normMeanRGB.size == 3) { "normMeanRGB length must be 3" }
    }

    fun checkOutBufferCapacity(
        outBuffer: FloatBuffer, outBufferOffset: Int, tensorWidth: Int, tensorHeight: Int
    ) {
        check(outBufferOffset + 3 * tensorWidth * tensorHeight <= outBuffer.capacity()) { "Buffer underflow" }
    }

    fun checkNormStdArg(normStdRGB: FloatArray) {
        require(normStdRGB.size == 3) { "normStdRGB length must be 3" }
    }
    checkOutBufferCapacity(outBuffer, outBufferOffset, width, height)
    checkNormMeanArg(normMeanRGB)
    checkNormStdArg(normStdRGB)
    require(!(memoryFormat != MemoryFormat.CONTIGUOUS && memoryFormat != MemoryFormat.CHANNELS_LAST)) { "Unsupported memory format $memoryFormat" }
    val pixelsCount = height * width
    val pixels = IntArray(pixelsCount)
    bitmap.getPixels(pixels, 0, width, x, y, width, height)
    if (MemoryFormat.CONTIGUOUS == memoryFormat) {
        val offsetB = 2 * pixelsCount
        for (i in 0 until pixelsCount) {
            val c = pixels[i]
            val r = (c shr 16 and 0xff) / 255.0f
            val g = (c shr 8 and 0xff) / 255.0f
            val b = (c and 0xff) / 255.0f
            outBuffer.put(outBufferOffset + i, (r - normMeanRGB[0]) / normStdRGB[0])
            outBuffer.put(outBufferOffset + pixelsCount + i, (g - normMeanRGB[1]) / normStdRGB[1])
            outBuffer.put(outBufferOffset + offsetB + i, (b - normMeanRGB[2]) / normStdRGB[2])
        }
    } else {
        for (i in 0 until pixelsCount) {
            val c = pixels[i]
            val r = (c shr 16 and 0xff) / 255.0f
            val g = (c shr 8 and 0xff) / 255.0f
            val b = (c and 0xff) / 255.0f
            outBuffer.put(outBufferOffset + 3 * i + 0, (r - normMeanRGB[0]) / normStdRGB[0])
            outBuffer.put(outBufferOffset + 3 * i + 1, (g - normMeanRGB[1]) / normStdRGB[1])
            outBuffer.put(outBufferOffset + 3 * i + 2, (b - normMeanRGB[2]) / normStdRGB[2])
        }
    }
}

/**
 * Calculate the remaining time according to the cost of each item
 *
 * @param current The index of current processing item
 * @param total The total num of all items
 * @param costPerItem In milliseconds
 * @return Seconds in Long that represent the remaining time
 */
fun calculateRemainingTime(
    current: Int, total: Int, costPerItem: Long
): Long {
    if (costPerItem.toInt() == 0) return 0L
    val remainItem = (total - current)
    return (remainItem * (costPerItem) / 1000)
}
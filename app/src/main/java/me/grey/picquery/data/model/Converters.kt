/**
 * Copyright 2023 Viacheslav Barkov
 */

package me.grey.picquery.data.model
import java.nio.ByteBuffer

fun FloatArray.toByteArray(): ByteArray {
    val bufferSize = this.size * 4
    val buffer = ByteBuffer.allocate(bufferSize)

    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this)
    val floatArray = FloatArray(this.size / 4)

    for (i in floatArray.indices) {
        floatArray[i] = buffer.float
    }
    return floatArray
}

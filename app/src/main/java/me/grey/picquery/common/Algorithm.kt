package me.grey.picquery.common

import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.math.sqrt

fun calculateSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
    var dotProduct = 0.0f
    var normA = 0.0f
    var normB = 0.0f

    for (i in vectorA.indices) {
        dotProduct += vectorA[i] * vectorB[i]
        normA += vectorA[i] * vectorA[i]
        normB += vectorB[i] * vectorB[i]
    }

    normA = sqrt(normA)
    normB = sqrt(normB)

    return dotProduct / (normA * normB)
}

// 模型将图片编码，返回的结果是FloatBuffer，转为ByteArray存储
fun FloatBuffer.toByteArray(): ByteArray {
    val bufferSize = this.remaining() * 4 // 假设每个浮点数占用 4 个字节
    val buffer = ByteBuffer.allocate(bufferSize)

    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

// 从数据库取出后，要进行向量计算，转为FloatArray
fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this)
    val floatArray = FloatArray(this.size / 4) // 假设每个浮点数占用 4 个字节

    buffer.asFloatBuffer().get(floatArray)
    return floatArray
}

//// 存储到数据库时，使用ByteArray，存储为BLOB格式
//fun FloatArray.toByteArray(): ByteArray {
//    val bufferSize = this.size * 4 // 假设每个浮点数占用 4 个字节
//    val buffer = ByteBuffer.allocate(bufferSize)
//
//    buffer.asFloatBuffer().put(this)
//    return buffer.array()
//}
package me.grey.picquery.common

import kotlin.math.sqrt

fun calculateSimilarity(vectorA: FloatArray, vectorB: FloatArray): Double {
    var dotProduct = 0.0f
    var normA = 0.0f
    var normB = 0.0f

    for (i in vectorA.indices) {
        val a = vectorA[i]
        val b = vectorB[i]
        dotProduct += a * b
        normA += a * a
        normB += b * b
    }

    val denominator = sqrt(normA) * sqrt(normB)
    return (if (denominator != 0.0f) dotProduct / denominator else 0.0f).toDouble()
}

package me.grey.picquery.common

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
package me.grey.picquery.common

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.pow
import kotlin.math.sqrt

fun calculateSimilarity(vectorA: FloatArray, vectorB: FloatArray): Double {
    var dotProduct = 0.0
    var normA = 0.0
    var normB = 0.0

    for (i in vectorA.indices) {
        dotProduct += vectorA[i] * vectorB[i]
        normA += vectorA[i] * vectorA[i]
        normB += vectorB[i] * vectorB[i]
    }

    normA = sqrt(normA)
    normB = sqrt(normB)

    return dotProduct / (normA * normB)
}

fun calculateSphericalDistanceLoss(vector1: FloatArray, vector2: FloatArray): Double {
    require(vector1.size == vector2.size) { "Vector dimensions do not match" }

    var dotProduct = 0.0
    var norm1 = 0.0
    var norm2 = 0.0

    for (i in vector1.indices) {
        dotProduct += vector1[i] * vector2[i]
        norm1 += vector1[i] * vector1[i]
        norm2 += vector2[i] * vector2[i]
    }

    norm1 = sqrt(norm1)
    norm2 = sqrt(norm2)

    val cosineSimilarity = dotProduct / (norm1 * norm2)
    val distanceLoss = acos(cosineSimilarity)

    return distanceLoss
}


fun sphericalDistLoss(x: FloatArray, y: FloatArray): Double {
    val xNormalized: DoubleArray = normalizeVector(x)
    val yNormalized: DoubleArray = normalizeVector(y)
    val difference = computeDifference(xNormalized, yNormalized)
    val magnitude = computeMagnitude(difference)
    return computeResult(magnitude)
}

fun normalizeVector(vector: FloatArray): DoubleArray {
    val norm = computeNorm(vector)
    return vector.map { it / norm }.toDoubleArray()
}

fun computeNorm(vector: FloatArray): Double {
    var sumOfSquares = 0.0
    for (element in vector) {
        sumOfSquares += element.pow(2)
    }
    return sqrt(sumOfSquares)
}

fun computeDifference(x: DoubleArray, y: DoubleArray): DoubleArray {
    return x.zip(y).map { (xValue, yValue) -> xValue - yValue }.toDoubleArray()
}

fun computeMagnitude(diff: DoubleArray): Double {
    var sumOfSquares = 0.0
    for (element in diff) {
        sumOfSquares += element.pow(2)
    }
    return sqrt(sumOfSquares)
}

fun computeResult(magnitude: Double): Double {
    return asin(magnitude / 2.0).pow(2) * 2.0
}
package me.grey.picquery.common

import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.asin

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

// Copy from:
// https://github.com/aallam/openai-kotlin/blob/main/openai-client/src/commonMain/kotlin/com.aallam.openai.client/extension/internal/CosineSimilarity.kt
internal object Cosine {

    /**
     * Compute similarity between two [FloatArray] vectors.
     */
    fun similarity(vec1: FloatArray, vec2: FloatArray): Double {
        if (vec1.contentEquals(vec2)) return 1.0
        return (vec1 dot vec2) / (norm(vec1) * norm(vec2))
    }

    /**
     * Compute distance between two [Double] vectors.
     */
    fun distance(vec1: FloatArray, vec2: FloatArray): Double {
        return 1.0F - similarity(vec1, vec2)
    }

    /** Dot product */
    private infix fun FloatArray.dot(vector: FloatArray): Double {
        return zip(vector).fold(0.0) { acc, (i, j) -> acc + (i * j) }
    }

    /** Compute the norm L2 : sqrt(sum(v²)). */
    private fun norm(vector: FloatArray): Double {
        val sum = vector.fold(0.0) { acc, cur -> acc + cur.toDouble().pow(2.0) }
        return sqrt(sum)
    }
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
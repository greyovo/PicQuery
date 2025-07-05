package me.grey.picquery.ui.search

data class SearchResult(
    val similarityScore: Float
) {
    enum class ConfidenceLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    val confidenceLevel: ConfidenceLevel = when {
        similarityScore < 0.25 -> ConfidenceLevel.LOW
        similarityScore < 0.8 -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.HIGH
    }
}

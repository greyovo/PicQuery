package me.grey.picquery.ui.simlilar

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf

@Stable
data class SimilarityConfiguration(
    val searchImageSimilarityThreshold: Float = 0.96f,
    val similarityGroupDelta: Float = 0.02f,
    val minSimilarityGroupSize: Int = 2
)
val LocalSimilarityConfig = compositionLocalOf { SimilarityConfiguration() }
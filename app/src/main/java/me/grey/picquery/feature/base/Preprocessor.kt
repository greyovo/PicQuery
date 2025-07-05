package me.grey.picquery.feature.base

import android.graphics.Bitmap

interface Preprocessor {
    suspend fun preprocessBatch(input: List<Bitmap>): Any

    suspend fun preprocess(input: Bitmap): Any
}

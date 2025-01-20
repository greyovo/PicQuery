package me.grey.picquery.feature.base

import android.graphics.Bitmap

fun interface ImageEncoder {
    suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray>
}
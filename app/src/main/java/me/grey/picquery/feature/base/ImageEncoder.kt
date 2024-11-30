package me.grey.picquery.feature.base

import android.graphics.Bitmap

interface ImageEncoder {

    suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray> {
        throw NotImplementedError()
    }
}
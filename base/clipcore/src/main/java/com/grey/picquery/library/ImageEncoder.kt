package com.grey.picquery.library

import android.graphics.Bitmap

interface ImageEncoder {

    suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray>
}
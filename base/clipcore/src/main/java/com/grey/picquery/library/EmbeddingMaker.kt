package com.grey.picquery.library

import android.graphics.Bitmap
import java.nio.FloatBuffer

interface EmbeddingMaker {
    suspend fun makeBatchEmbedding(input: List<Bitmap>): FloatBuffer
}
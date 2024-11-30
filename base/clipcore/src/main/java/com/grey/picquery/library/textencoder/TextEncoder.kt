package com.grey.picquery.library.textencoder

interface TextEncoder {
    fun encode(input: String): FloatArray
}
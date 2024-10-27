package me.grey.picquery.feature.base

interface TextEncoder {
    fun encode(input: String): FloatArray
}
package me.grey.picquery.feature.base

fun interface TextEncoder {
    fun encode(input: String): FloatArray
}
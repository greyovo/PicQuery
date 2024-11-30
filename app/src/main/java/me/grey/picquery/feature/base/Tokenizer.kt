package me.grey.picquery.feature.base

abstract class Tokenizer(
    protected var contextLength: Int = 77,
    protected var truncate: Boolean = false,
) {

    abstract fun tokenize(
        text: String,
    ): Pair<IntArray, LongArray>
}
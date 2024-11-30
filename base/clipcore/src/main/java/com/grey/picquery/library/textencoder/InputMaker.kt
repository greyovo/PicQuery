package com.grey.picquery.library.textencoder

interface TextInputMaker<T> {
    fun makeInput(text: String): T
}
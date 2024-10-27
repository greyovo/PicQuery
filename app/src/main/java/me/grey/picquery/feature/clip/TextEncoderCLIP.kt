package me.grey.picquery.feature.clip

import android.content.Context
import me.grey.picquery.feature.TextEncoderImpl

class TextEncoderCLIP(context: Context): TextEncoderImpl(context) {
    override val modelPath: String = "clip-text-int8.ort"
    override val modelType: Int = 0
}
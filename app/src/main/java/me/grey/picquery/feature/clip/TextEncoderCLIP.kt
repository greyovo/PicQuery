package me.grey.picquery.feature.clip

import android.content.Context
import me.grey.picquery.feature.TextEncoderONNX

class TextEncoderCLIP(context: Context) : TextEncoderONNX(context) {
    override val modelPath: String = "clip-text-int8.ort"
    override val modelType: Int = 0
}

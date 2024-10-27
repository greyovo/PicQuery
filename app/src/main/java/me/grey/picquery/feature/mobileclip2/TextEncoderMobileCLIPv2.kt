package me.grey.picquery.feature.mobileclip2

import android.content.Context
import me.grey.picquery.feature.TextEncoderImpl

class TextEncoderMobileCLIPv2(context: Context) : TextEncoderImpl(context) {
    override val modelPath: String = "text_model.ort"
    override val modelType: Int = 1
}
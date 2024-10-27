package me.grey.picquery.feature.mobileclip

import android.content.Context
import me.grey.picquery.feature.TextEncoderImpl

class TextEncoderMobileCLIP(context: Context): TextEncoderImpl(context) {
    override val modelPath: String = "text_model.ort"
    override val modelType: Int = 1
}
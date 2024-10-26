package com.grey.clip.textEncoder

import android.content.Context
import com.grey.picquery.library.textencoder.TextEncoderImpl

class ClipTextEncoder(context: Context): TextEncoderImpl(context) {
    override val modelPath: String = "clip-text-int8.ort"
    override val modelType: Int = 0
}
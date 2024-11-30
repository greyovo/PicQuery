package com.grey.picquery.mobileclip.textEncoder

import android.content.Context
import com.grey.picquery.library.textencoder.TextEncoderImpl

class MobileClipTextEncoder(context: Context): TextEncoderImpl(context) {
    override val modelPath: String = "text_model.ort"
    override val modelType: Int = 1
}
package me.grey.picquery.feature.tf

import android.content.Context

class TextEncoderTFLite(context: Context) : TextEncoderTF(context) {
    override val modelPath: String = MODEL_PATH

    companion object {
        const val MODEL_PATH = "text_model.tflite"
    }
}

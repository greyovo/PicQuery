package me.grey.picquery.feature.tf

import android.content.Context

class TextEncoderTFLite(
    context: Context,
    runtimeConfig: TFLiteRuntimeConfig = TFLiteRuntimeConfig.Default
) : TextEncoderTF(context, runtimeConfig) {
    override val modelPath: String = MODEL_PATH

    companion object {
        const val MODEL_PATH = "text_model_dynamic_wi8.tflite"
    }
}

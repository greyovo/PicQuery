package me.grey.picquery.feature.tf

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import me.grey.picquery.feature.mobileclip2.PreprocessorMobileCLIPv2

class ImageEncoderTFLite(
    context: Context,
    preprocessor: PreprocessorMobileCLIPv2,
    dispatcher: CoroutineDispatcher,
    runtimeConfig: TFLiteRuntimeConfig = TFLiteRuntimeConfig.Default
) : ImageEncoderTF(
    context = context,
    modelPath = MODEL_PATH,
    preprocessor = preprocessor,
    dispatcher = dispatcher,
    runtimeConfig = runtimeConfig
) {
    companion object {
        const val MODEL_PATH = "image_model.tflite"
    }
}

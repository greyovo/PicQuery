package me.grey.picquery.feature.mobileclip2

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import me.grey.picquery.feature.tf.ImageEncoderTF
import me.grey.picquery.feature.tf.TFLiteRuntimeConfig

class ImageEncoderMobileCLIPv2(
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
        const val MODEL_PATH = "mobileclip-image.tflite"
    }
}

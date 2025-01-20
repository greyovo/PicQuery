package me.grey.picquery.feature.mobileclip

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import me.grey.picquery.feature.ImageEncoderONNX


class ImageEncoderMobileCLIP(context: Context, preprocessor: PreprocessorMobileCLIP,dispatcher: CoroutineDispatcher) :
    ImageEncoderONNX(
        INPUT.toLong(), "vision_model.ort", context, preprocessor, dispatcher
    ) {
    companion object {
        const val INPUT = 256
    }
}

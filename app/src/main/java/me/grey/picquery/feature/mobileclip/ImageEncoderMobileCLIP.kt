package me.grey.picquery.feature.mobileclip

import android.content.Context
import me.grey.picquery.feature.ImageEncoderONNX


class ImageEncoderMobileCLIP(context: Context, preprocessor: PreprocessorMobileCLIP) :
    ImageEncoderONNX(
        INPUT.toLong(), "vision_model.ort", context, preprocessor
    ) {
    companion object {
        const val INPUT = 256
    }
}

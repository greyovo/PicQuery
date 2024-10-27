package me.grey.picquery.feature.mobileclip

import android.content.Context
import me.grey.picquery.feature.ImageEncoderImpl
import me.grey.picquery.feature.clip.PreprocessorCLIP


class ImageEncoderMobileCLIP(context: Context, preprocessor: PreprocessorCLIP) :
    ImageEncoderImpl(
        INPUT.toLong(), "vision_model.ort", context, preprocessor
    ) {
    companion object {
        const val INPUT = 256
    }
}

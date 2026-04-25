package me.grey.picquery.feature.tf

import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.TextEncoder
import me.grey.picquery.feature.mobileclip2.PreprocessorMobileCLIPv2
import org.koin.dsl.module

val modulesTF = module {
    single<TextEncoder> { TextEncoderTFLite(get()) }
    single<PreprocessorMobileCLIPv2> { PreprocessorMobileCLIPv2() }
    factory<ImageEncoder> {
        ImageEncoderTFLite(
            context = get(),
            preprocessor = get<PreprocessorMobileCLIPv2>(),
            dispatcher = get()
        )
    }
}

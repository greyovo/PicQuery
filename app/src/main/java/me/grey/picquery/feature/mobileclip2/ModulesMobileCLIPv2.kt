package me.grey.picquery.feature.mobileclip2

import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.TextEncoder
import org.koin.dsl.module
import org.koin.dsl.onClose

val modulesMobileCLIP2 = module {
    single<TextEncoder> { TextEncoderMobileCLIPv2(get()) }
        .onClose { (it as? TextEncoderMobileCLIPv2)?.closeSession() }
    single<PreprocessorMobileCLIPv2> { PreprocessorMobileCLIPv2() }
    single<ImageEncoder> {
        ImageEncoderMobileCLIPv2(
            context = get(),
            preprocessor = get<PreprocessorMobileCLIPv2>(),
            dispatcher = get()
        )
    }.onClose { (it as? ImageEncoderMobileCLIPv2)?.closeSession() }
}

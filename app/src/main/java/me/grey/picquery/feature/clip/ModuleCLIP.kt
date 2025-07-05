package me.grey.picquery.feature.clip

import me.grey.picquery.feature.base.ImageEncoder
import me.grey.picquery.feature.base.TextEncoder
import org.koin.dsl.module

val modulesCLIP = module {
    single<PreprocessorCLIP> { PreprocessorCLIP() }
    single<TextEncoder> { TextEncoderCLIP(get()) }
    factory<ImageEncoder> {
        ImageEncoderCLIP(
            context = get(),
            preprocessor = get<PreprocessorCLIP>(),
            dispatcher = get()
        )
    }
}

package com.grey.picquery.mobileclip

import com.grey.picquery.library.EmbeddingMaker
import com.grey.picquery.library.ImageEncoder
import com.grey.picquery.library.textencoder.TextEncoder
import com.grey.picquery.mobileclip.imageEncoder.MobileClipEmbeddingMaker
import com.grey.picquery.mobileclip.imageEncoder.MobileClipImageEncoder
import com.grey.picquery.mobileclip.textEncoder.MobileClipTextEncoder
import org.koin.dsl.module

val encoderModule = module {
    single <EmbeddingMaker>{ MobileClipEmbeddingMaker() }
    single <TextEncoder> { MobileClipTextEncoder(get()) }
    factory<ImageEncoder> { MobileClipImageEncoder(context = get(), mobileClipEmbeddingMaker =get<EmbeddingMaker>()) }
}

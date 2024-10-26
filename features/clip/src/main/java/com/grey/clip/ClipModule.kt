package com.grey.clip

import com.grey.clip.imageEncoder.ClipEmbeddingMaker
import com.grey.clip.imageEncoder.ClipImageEncoder
import com.grey.clip.textEncoder.ClipTextEncoder
import com.grey.picquery.library.EmbeddingMaker
import com.grey.picquery.library.ImageEncoder
import com.grey.picquery.library.textencoder.TextEncoder

import org.koin.dsl.module

val clipEncoderModule = module {
    single <EmbeddingMaker>{ ClipEmbeddingMaker() }
    single <TextEncoder> { ClipTextEncoder(get()) }
    factory<ImageEncoder> { ClipImageEncoder(context = get(), embeddingMaker =get()) }
}

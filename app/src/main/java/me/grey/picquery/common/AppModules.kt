package me.grey.picquery.common

import me.grey.picquery.core.ImageSearcher
import me.grey.picquery.core.encoder.ImageEncoder
import me.grey.picquery.core.encoder.TextEncoder
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.ui.albums.AlbumViewModel
import me.grey.picquery.ui.search.SearchViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val viewModelModules = module {
//    viewModel { SearchViewModel() }
//    viewModel { AlbumViewModel() }
    single { AlbumViewModel() }
    single { SearchViewModel() }
}

private val dataModules = module {
    single { AlbumRepository(androidContext().contentResolver) }
    single { EmbeddingRepository() }
    single { PhotoRepository(androidContext().contentResolver) }

}

private val domainModules = module {
    single { ImageSearcher() }

    single { ImageEncoder() }
    single { TextEncoder() }
}

val AppModules = listOf(viewModelModules, dataModules, domainModules)
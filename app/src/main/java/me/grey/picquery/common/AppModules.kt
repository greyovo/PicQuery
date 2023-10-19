package me.grey.picquery.common

import androidx.room.Room
import me.grey.picquery.data.AppDatabase
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher
import me.grey.picquery.domain.MLKitTranslator
import me.grey.picquery.domain.encoder.ImageEncoder
import me.grey.picquery.domain.encoder.TextEncoder
import me.grey.picquery.ui.display.DisplayViewModel
import me.grey.picquery.ui.home.HomeViewModel
import me.grey.picquery.ui.search.SearchViewModel
import me.grey.picquery.ui.setting.SettingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

private val viewModelModules = module {
    viewModel {
        HomeViewModel(
            albumManager = get(),
            imageSearcher = get()
        )
    }
    viewModel {
        SearchViewModel(imageSearcher = get())
    }
    viewModel {
        DisplayViewModel(photoRepository = get(), imageSearcher = get())
    }

    viewModel { SettingViewModel(preferenceRepository = get()) }
}

private val dataModules = module {
    // SQLite Database
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java, "app-db"
        ).build()
    }

    single { AlbumRepository(androidContext().contentResolver, database = get()) }
    single { EmbeddingRepository(database = get()) }
    single { PhotoRepository(androidContext().contentResolver) }
    single { PreferenceRepository() }
}

private val domainModules = module {
    single {
        ImageSearcher(
            imageEncoder = get(),
            textEncoder = get(),
            embeddingRepository = get(),
            contentResolver = androidContext().contentResolver,
            translator = MLKitTranslator()
        )
    }
    single {
        AlbumManager(
            albumRepository = get(),
            photoRepository = get(),
            imageSearcher = get(),
        )
    }

    single { ImageEncoder() }
    single { TextEncoder() }

    single { MLKitTranslator() }
}

val AppModules = listOf(viewModelModules, dataModules, domainModules)


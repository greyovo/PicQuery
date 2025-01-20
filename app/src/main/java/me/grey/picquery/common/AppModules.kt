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
import me.grey.picquery.feature.clip.modulesCLIP
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
            imageSearcher = get()
        )
    }
    viewModel {
        SearchViewModel(imageSearcher = get(), ioDispatcher = get())
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
            translator = MLKitTranslator(),
            dispatcher = get()
        )
    }
    single {
        AlbumManager(
            albumRepository = get(),
            photoRepository = get(),
            imageSearcher = get(),
            ioDispatcher = get()
        )
    }

    single { MLKitTranslator() }
}

// need inject encoder here
val AppModules = listOf(dispatchersKoinModule, viewModelModules, dataModules, modulesCLIP, domainModules, )


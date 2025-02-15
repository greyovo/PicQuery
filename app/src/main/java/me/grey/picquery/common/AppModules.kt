package me.grey.picquery.common

import androidx.work.WorkManager
import me.grey.picquery.data.AppDatabase
import me.grey.picquery.data.ObjectBoxDatabase
import me.grey.picquery.data.dao.EmbeddingDao
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher
import me.grey.picquery.domain.MLKitTranslator
import me.grey.picquery.domain.SimilarityManager
import me.grey.picquery.feature.clip.modulesCLIP
import me.grey.picquery.ui.display.DisplayViewModel
import me.grey.picquery.ui.home.HomeViewModel
import me.grey.picquery.ui.photoDetail.PhotoDetailViewModel
import me.grey.picquery.ui.search.SearchViewModel
import me.grey.picquery.ui.setting.SettingViewModel
import me.grey.picquery.ui.simlilar.SimilarPhotosViewModel
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
        SearchViewModel(imageSearcher = get(), ioDispatcher = get(), get())
    }
    viewModel {
        DisplayViewModel(photoRepository = get(), imageSearcher = get())
    }


    viewModel { SettingViewModel(preferenceRepository = get()) }
    viewModel { SimilarPhotosViewModel(get(),get(),get(),get(),get()) }
    viewModel { PhotoDetailViewModel(get(),get()) }
}

private val dataModules = module {
    // SQLite Database
    single {
        AppDatabase.getDatabase(androidContext())
    }
    single<EmbeddingDao> { get<AppDatabase>().embeddingDao() }
    single { get<AppDatabase>().imageSimilarityDao() }
    single { AlbumRepository(androidContext().contentResolver, database = get()) }
    single { EmbeddingRepository(dataSource = get()) }
    single { ObjectBoxEmbeddingRepository(dataSource = ObjectBoxDatabase.getDatabase().embeddingDao()) }
    single { PhotoRepository(androidContext()) }
    single { PreferenceRepository() }
}

private val domainModules = module {
    single {
        ImageSearcher(
            imageEncoder = get(),
            textEncoder = get(),
            embeddingRepository = get(),
            objectBoxEmbeddingRepository = get(),
            translator = MLKitTranslator(),
            dispatcher = get()
        )
    }
    single {
        AlbumManager(
            albumRepository = get(),
            photoRepository = get(),
            embeddingRepository = get(),
            imageSearcher = get(),
            ioDispatcher = get()
        )
    }

    single { MLKitTranslator() }

    single { SimilarityManager(get(),get()) }
}

val workManagerModule = module {
    single { WorkManager.getInstance(get()) }
}

// need inject encoder here
val AppModules = listOf(dispatchersKoinModule, viewModelModules, dataModules, modulesCLIP, domainModules, workManagerModule)


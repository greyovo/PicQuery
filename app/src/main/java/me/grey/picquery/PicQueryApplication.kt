package me.grey.picquery

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import me.grey.picquery.common.AppModules
import me.grey.picquery.data.ObjectBoxDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class PicQueryApplication : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
        private const val TAG = "PicQueryApp"
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        startKoin {
            androidLogger()
            androidContext(this@PicQueryApplication)
            modules(AppModules)
            ObjectBoxDatabase.getDatabase().initialize(this@PicQueryApplication)
        }
    }
}

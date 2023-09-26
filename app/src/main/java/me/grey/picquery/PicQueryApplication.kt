package me.grey.picquery

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import me.grey.picquery.common.AppModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin


class PicQueryApplication : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        startKoin {
            androidLogger()
            androidContext(this@PicQueryApplication)
            modules(AppModules)
        }
    }

}
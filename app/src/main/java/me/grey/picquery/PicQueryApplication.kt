package me.grey.picquery

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import me.grey.picquery.common.AppModules
import me.grey.picquery.common.initCrashCallback
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin


class PicQueryApplication : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
        private const val TAG = "PicQueryApp"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

//        XCrash.init(this)
        initCrashCallback()
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
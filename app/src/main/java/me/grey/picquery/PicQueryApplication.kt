package me.grey.picquery

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
        private lateinit var appContext: Context
        private const val TAG = "PicQueryApp"

        /**
         * 获取 Application Context，避免内存泄漏
         * 总是返回 applicationContext 而不是直接持有引用
         */
        val context: Context
            get() = appContext.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
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

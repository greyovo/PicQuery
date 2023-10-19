package me.grey.picquery

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import me.grey.picquery.common.AppModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin


class PicQueryApplication : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
        private const val TAG = "PicQueryApp"
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

    // =========

    private val showAgreement = mutableStateOf(false)

    @Composable
    fun AgreementDialog() {

    }
}
package me.grey.picquery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import me.grey.picquery.theme.PicQueryThemeM3
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.annotation.KoinExperimentalAPI

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @OptIn(KoinExperimentalAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KoinAndroidContext {
                PicQueryThemeM3 {
                    AppNavHost()
                }
            }
        }
    }

}


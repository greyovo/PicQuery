package me.grey.picquery.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import me.grey.picquery.theme.PicQueryThemeM3

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicQueryThemeM3 {
                AppNavHost()
            }
        }
    }

}


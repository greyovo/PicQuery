package me.grey.picquery.ui.result

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity

class SearchResultActivity : FragmentActivity() {

    private val viewModel: SearchResultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra("text")
        if (text != null) {
            viewModel.startSearch(text)
        }
        setContent {
            SearchResultScreen(onBack = { this.finish() })
        }
    }

}
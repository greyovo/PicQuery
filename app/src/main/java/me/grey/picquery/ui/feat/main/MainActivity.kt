package me.grey.picquery.ui.feat.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import me.grey.picquery.data.model.Album
import me.grey.picquery.ui.theme.PicQueryTheme

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        val mBottomTabItems =
            listOf(
                BottomItem("搜索", Icons.Filled.Search, Icons.Outlined.Search),
                BottomItem("相册", Icons.Filled.Menu, Icons.Outlined.Menu),
            )
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var albumList: List<Album>? = null
            var indexedAlbumList: List<Album>? = null
            mainViewModel.albumList.observe(this) {
                albumList = it
            }
            mainViewModel.indexedAlbumList.observe(this) {
                indexedAlbumList = it
            }
            PicQueryTheme {
                var bottomSelectedIndex by remember { mutableStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavBottomBar(bottomSelectedIndex, mBottomTabItems) {
                            bottomSelectedIndex = it
                            println(bottomSelectedIndex)
                        }
                    }
                ) {
                    if (bottomSelectedIndex == 0) {
                        SearchScreen(indexedAlbumList)
                    } else {
                        AlbumListScreen(albumList)
                    }
                }
            }
        }
    }
}



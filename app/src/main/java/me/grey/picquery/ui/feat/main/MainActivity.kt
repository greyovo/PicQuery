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
import me.grey.picquery.common.showConfirmDialog
import me.grey.picquery.common.showToast
import me.grey.picquery.ui.theme.PicQueryTheme

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        val bottomTabItems =
            listOf(
                BottomItem("搜索", Icons.Filled.Search, Icons.Outlined.Search),
                BottomItem("相册", Icons.Filled.Menu, Icons.Outlined.Menu),
            )
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainScaffold() }
    }

    @Composable
    private fun MainScaffold() {
        var bottomSelectedIndex by remember { mutableStateOf(0) }
        var albumList by remember { mutableStateOf(mainViewModel.albumList.value) }
        var searchableList by remember { mutableStateOf(mainViewModel.searchableAlbumList.value) }
        var unsearchableList by remember { mutableStateOf(mainViewModel.unsearchableAlbumList.value) }

//        mainViewModel.albumList.observe(this) { albumList = it }
        mainViewModel.searchableAlbumList.observe(this) { searchableList = it }
        mainViewModel.unsearchableAlbumList.observe(this) { unsearchableList = it }
        PicQueryTheme {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavBottomBar(bottomSelectedIndex, bottomTabItems) { selected ->
                        bottomSelectedIndex = selected
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
//                            showToast("更新索引！")
                            showConfirmDialog(this, "提示", "asd")
                        },
                        backgroundColor = MaterialTheme.colors.primary,
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "更新索引")
                    }
                }
            ) {
                if (bottomSelectedIndex == 0) {
                    SearchScreen(searchableList, unsearchableList)
                } else {
                    AlbumListScreen(albumList)
                }
            }
        }
    }
}




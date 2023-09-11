package me.grey.picquery.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.permissionx.guolindev.PermissionX
import me.grey.picquery.R
import me.grey.picquery.theme.PicQueryTheme
import me.grey.picquery.themeM3.PicQueryThemeM3
import me.grey.picquery.ui.albums.AlbumListScreen
import me.grey.picquery.ui.search.SearchScreenM3

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
        initAlbum()
    }

    private fun initAlbum() {
        for (p in permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                requestPermission()
                return
            }
        }
        mainViewModel.initAll()
    }

    private val permissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        else
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun requestPermission() {
        PermissionX.init(this)
            .permissions(permissions)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    resources.getString(R.string.permission_tips),
                    resources.getString(R.string.ok),
                    resources.getString(R.string.cancel),
                )
            }.request { allGranted, _, _ ->
                if (!allGranted) {
                    Toast.makeText(this, "无法获取相应权限", Toast.LENGTH_LONG).show()
                } else {
                    mainViewModel.initAll()
                }
            }
    }

    @Composable
    private fun MainScaffold() {
        val bottomSelectedIndex = remember { mutableIntStateOf(0) }
        val albumList = remember { mainViewModel.albumList }
        val searchableList = remember { mainViewModel.searchableAlbumList }
        val unsearchableList = remember { mainViewModel.unsearchableAlbumList }

        PicQueryThemeM3 {
            Scaffold(
                bottomBar = {
                    NavBottomBar(bottomSelectedIndex.intValue, bottomTabItems) { selected ->
                        bottomSelectedIndex.intValue = selected
                    }
                },
            ) { padding ->
                if (bottomSelectedIndex.intValue == 0) {
                    SearchScreenM3(
                        searchableList, unsearchableList,
                        onAddIndex = {
                            mainViewModel.encodeAlbum(
                                album = it,
                            )
                        },
                        onRemoveIndex = {
                            // 移除某个相册的编码
                        },
                        paddingValues = padding
                    )
                } else {
                    AlbumListScreen(albumList, padding)
                }
            }
        }
    }
}




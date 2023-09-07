package me.grey.picquery.ui.feat.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.permissionx.guolindev.PermissionX
import me.grey.picquery.R
import me.grey.picquery.theme.PicQueryTheme

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
        mainViewModel.initAllAlbumList()
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
                    mainViewModel.initAllAlbumList()
                }
            }
    }

    @Composable
    private fun MainScaffold() {
        var bottomSelectedIndex by remember { mutableStateOf(0) }
        val albumList by remember { mutableStateOf(mainViewModel.albumList.value) }
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
            ) { _ ->
                if (bottomSelectedIndex == 0) {
                    SearchScreen(
                        searchableList, unsearchableList,
                        onAddIndex = {
                            mainViewModel.encodeAlbum(
                                album = it,
                            )
                        },
                        onRemoveIndex = {
                            // 移除某个相册的编码
                        },
                    )
                } else {
                    AlbumListScreen(albumList)
                }
            }
        }
    }
}




package me.grey.picquery.ui.home

import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import me.grey.picquery.common.InitializeEffect
import me.grey.picquery.domain.AlbumManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InitPermissions(homeViewModel: HomeViewModel = koinViewModel(), albumManager: AlbumManager = koinInject()) {
    val mediaPermissions = rememberMediaPermissions()
    InitializeEffect {
        if (mediaPermissions.allPermissionsGranted) {
            // 权限已授予，初始化相册列表
            albumManager.initAllAlbumList()
            homeViewModel.doneRequestPermission()
        } else {
            // 没有权限，显示引导（但只在没有数据的情况下）
            // userGuideVisible 的状态由 HomeViewModel 的 init 决定
        }
    }
}

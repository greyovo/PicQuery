package me.grey.picquery.ui.home

import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import me.grey.picquery.common.InitializeEffect
import me.grey.picquery.domain.AlbumManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InitPermissions(
    homeViewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject()
) {
    val mediaPermissions = rememberMediaPermissions()
    InitializeEffect {
        if (mediaPermissions.allPermissionsGranted) {
            albumManager.initAllAlbumList()
        } else {
            homeViewModel.showUserGuide()
        }
    }
}
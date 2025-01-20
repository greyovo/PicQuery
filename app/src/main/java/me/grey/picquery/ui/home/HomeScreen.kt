package me.grey.picquery.ui.home

import AppBottomSheetState
import LogoRow
import SearchInput
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.common.Constants
import me.grey.picquery.common.showToast
import me.grey.picquery.domain.AlbumManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import rememberAppBottomSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject(),
    navigateToSearch: (String) -> Unit,
    navigateToSearchWitImage: (Uri) -> Unit,
    navigateToSetting: () -> Unit,
) {
    InitPermissions()

    val scope = rememberCoroutineScope()
    val userGuideVisible = remember { homeViewModel.userGuideVisible }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val albumListSheetState = rememberAppBottomSheetState()

    // Handle bottom sheet
    if (albumListSheetState.isVisible) {
        AddAlbumBottomSheet(
            sheetState = albumListSheetState,
            onStartIndexing = { homeViewModel.doneIndexAlbum() },
        )
    }

    // Main scaffold
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            HomeFloatingButton(
                userGuideVisible = userGuideVisible.value,
                albumManager = albumManager,
                scope = scope,
                albumListSheetState = albumListSheetState,
                navigateToSetting = navigateToSetting
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = { EncodingProgressBar() },
        topBar = {
            HomeTopBar(
                onClickHelpButton = { homeViewModel.showUserGuide() }
            )
        }
    ) { padding ->
        MainContent(
            padding = padding,
            userGuideVisible = userGuideVisible.value,
            homeViewModel = homeViewModel,
            navigateToSearch = navigateToSearch,
            navigateToSearchWitImage = navigateToSearchWitImage,
            albumListSheetState = albumListSheetState
        )
    }
}

@Composable
private fun HomeFloatingButton(
    userGuideVisible: Boolean,
    albumManager: AlbumManager,
    scope: CoroutineScope,
    albumListSheetState: AppBottomSheetState,
    navigateToSetting: () -> Unit
) {
    if (!userGuideVisible) {
        val busyToastText = stringResource(R.string.busy_when_add_album_toast)
        HomeBottomActions(
            onClickManageAlbum = {
                if (albumManager.isEncoderBusy) {
                    showToast(busyToastText)
                } else {
                    scope.launch { albumListSheetState.show() }
                }
            },
            navigateToSetting = navigateToSetting,
        )
    }
}

@Composable
private fun MainContent(
    padding: PaddingValues,
    userGuideVisible: Boolean,
    homeViewModel: HomeViewModel,
    navigateToSearch: (String) -> Unit,
    navigateToSearchWitImage: (Uri) -> Unit,
    albumListSheetState: AppBottomSheetState
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxHeight(0.75f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchSection(
            userGuideVisible = userGuideVisible,
            homeViewModel = homeViewModel,
            navigateToSearch = navigateToSearch,
            navigateToSearchWitImage = navigateToSearchWitImage
        )

        GuideSection(
            userGuideVisible = userGuideVisible,
            homeViewModel = homeViewModel,
            albumListSheetState = albumListSheetState
        )
    }
}

@OptIn(InternalTextApi::class)
@Composable
private fun SearchSection(
    userGuideVisible: Boolean,
    homeViewModel: HomeViewModel,
    navigateToSearch: (String) -> Unit,
    navigateToSearchWitImage: (Uri) -> Unit
) {
    AnimatedVisibility(visible = !userGuideVisible) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoRow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            
            val searchText by homeViewModel.searchText.collectAsState()
            SearchInput(
                queryText = searchText,
                onStartSearch = { text ->
                    if (text.isNotEmpty()) {
                        navigateToSearch(text)
                    }
                },
                onQueryChange = { homeViewModel.onQueryChange(it) },
                onImageSearch = { uri ->
                    if (uri.toString().isNotEmpty()) {
                        Log.d("HomeScreen", "Selected URI: $uri")
                        navigateToSearchWitImage(uri)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun GuideSection(
    userGuideVisible: Boolean,
    homeViewModel: HomeViewModel,
    albumListSheetState: AppBottomSheetState
) {
    AnimatedVisibility(visible = userGuideVisible) {
        val scope = rememberCoroutineScope()
        val currentStep = remember { homeViewModel.currentGuideState }
        val mediaPermissions = rememberMediaPermissions()
        
        UserGuide(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            onRequestPermission = { mediaPermissions.launchMultiplePermissionRequest() },
            onOpenAlbum = { scope.launch { albumListSheetState.show() } },
            onFinish = { homeViewModel.finishGuide() },
            state = currentStep.value,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberMediaPermissions(
    homeViewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject(),
): MultiplePermissionsState {
    val scope = rememberCoroutineScope()
    return rememberMultiplePermissionsState(
        permissions = Constants.PERMISSIONS,
        onPermissionsResult = { permission ->
            if (permission.all { it.value }) {
                homeViewModel.doneRequestPermission()
                scope.launch { albumManager.initAllAlbumList() }
            }
        },
    )
}
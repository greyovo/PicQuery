package me.grey.picquery.ui.home

import LogoRow
import SearchInput
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.common.Constants
import me.grey.picquery.common.showToast
import me.grey.picquery.domain.AlbumManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import rememberAppBottomSheetState

@OptIn(InternalTextApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject(),
    navigateToSearch: (String) -> Unit,
    navigateToSetting: () -> Unit,
) {
    InitPermissions()

    // === BottomSheet block
    val albumListSheetState = rememberAppBottomSheetState()
    if (albumListSheetState.isVisible) {
        AddAlbumBottomSheet(
            sheetState = albumListSheetState,
            onStartIndexing = { homeViewModel.doneIndexAlbum() },
        )
    }
    // === BottomSheet end

    val scope = rememberCoroutineScope()
    val userGuideVisible = remember { homeViewModel.userGuideVisible }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // === UI block
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            val busyToastText = stringResource(R.string.busy_when_add_album_toast)
            if (!userGuideVisible.value) {
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
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = { EncodingProgressBar() },
        topBar = {
            HomeTopBar(
                onClickHelpButton = {
                    homeViewModel.showUserGuide()
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxHeight(0.75f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Central SearchInput bar
            AnimatedVisibility(visible = !userGuideVisible.value) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LogoRow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    SearchInput(
                        queryText = remember { homeViewModel.searchText },
                        onStartSearch = { text ->
                            if (text.isNotEmpty()) {
                                homeViewModel.searchText.value = text
                                navigateToSearch(text)
                            }
                        },
                    )
                }
            }

            // User Guide
            AnimatedVisibility(visible = userGuideVisible.value) {
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
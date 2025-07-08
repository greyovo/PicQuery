package me.grey.picquery.ui.home

import AppBottomSheetState
import LogoRow
import SearchInput
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
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
import me.grey.picquery.domain.ImageSearcher
import me.grey.picquery.ui.search.SearchConfigBottomSheet
import me.grey.picquery.ui.search.SearchRangeBottomSheet
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
    navigateToSimilar: () -> Unit
) {
    InitPermissions()

    val userGuideVisible = remember { homeViewModel.userGuideVisible }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val albumListSheetState = rememberAppBottomSheetState()

    // Handle bottom sheet
    if (albumListSheetState.isVisible) {
        AddAlbumBottomSheet(
            sheetState = albumListSheetState,
            onStartIndexing = { homeViewModel.doneIndexAlbum() }
        )
    }

    // Main scaffold
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = { EncodingProgressBar() },
        topBar = {
            HomeTopBar(
                onClickHelpButton = homeViewModel::showUserGuide,
                navigateToSimilar = navigateToSimilar,
                navigateToSetting = navigateToSetting,
                albumListSheetState = albumListSheetState,
                albumManager = albumManager,
                imageSearcher = koinInject()
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
        horizontalAlignment = Alignment.CenterHorizontally
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
            horizontalAlignment = Alignment.CenterHorizontally
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
                        navigateToSearchWitImage(uri)
                    }
                }
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
            state = currentStep.value
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberMediaPermissions(
    homeViewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject()
): MultiplePermissionsState {
    val scope = rememberCoroutineScope()
    return rememberMultiplePermissionsState(
        permissions = Constants.PERMISSIONS,
        onPermissionsResult = { permission ->
            if (permission.all { it.value }) {
                homeViewModel.doneRequestPermission()
                scope.launch { albumManager.initAllAlbumList() }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onClickHelpButton: () -> Unit,
    navigateToSimilar: () -> Unit,
    navigateToSetting: () -> Unit,
    albumListSheetState: AppBottomSheetState = rememberAppBottomSheetState(),
    albumManager: AlbumManager = koinInject(),
    imageSearcher: ImageSearcher = koinInject()
) {
    var showSearchFilterBottomSheet by remember { mutableStateOf(false) }
    var showSearchRangeBottomSheet by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hint = stringResource(R.string.busy_when_add_album_toast)
    TopAppBar(
        title = { Text("PicQuery") },
        actions = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.similar_photos)) },
                        onClick = {
                            expanded = false
                            navigateToSimilar()
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_similar),
                                contentDescription = "Similar Photos",
                                modifier = Modifier.size(width = 24.dp, height = 24.dp)
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_index_albums)) },
                        onClick = {
                            expanded = false
                            if (!albumManager.isEncoderBusy) {
                                scope.launch { albumListSheetState.show() }
                            } else {
                                showToast(hint)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Index Albums"
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_search_range)) },
                        onClick = {
                            expanded = false
                            showSearchRangeBottomSheet = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "Search Range"
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.image_search_config_title)) },
                        onClick = {
                            expanded = false
                            showSearchFilterBottomSheet = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Search Configuration"
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_settings)) },
                        onClick = {
                            expanded = false
                            navigateToSetting()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    )
                }
            }

            IconButton(onClick = onClickHelpButton) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = "Help"
                )
            }
        }
    )

    if (showSearchFilterBottomSheet) {
        SearchConfigBottomSheet(
            imageSearcher = imageSearcher,
            onDismiss = { showSearchFilterBottomSheet = false }
        )
    }
    if (showSearchRangeBottomSheet) {
        SearchRangeBottomSheet(dismiss = {
            showSearchRangeBottomSheet = false
        })
    }
}

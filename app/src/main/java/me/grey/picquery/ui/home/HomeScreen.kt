package me.grey.picquery.ui.home

import AppBottomSheetState
import LogoImage
import LogoRow
import LogoText
import SearchInput
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
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
    val imageSearcher: ImageSearcher = koinInject()
    var showSearchFilterBottomSheet by remember { mutableStateOf(false) }
    var showSearchRangeBottomSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busyHint = stringResource(R.string.busy_when_add_album_toast)
    val onOpenIndexAlbums: () -> Unit = {
        if (!albumManager.isEncoderBusy) {
            scope.launch { albumListSheetState.show() }
        } else {
            showToast(busyHint)
        }
    }

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
                onOpenIndexAlbums = onOpenIndexAlbums,
                onOpenSearchRange = { showSearchRangeBottomSheet = true },
                onOpenSearchConfig = { showSearchFilterBottomSheet = true },
                navigateToSimilar = navigateToSimilar,
                navigateToSetting = navigateToSetting
            )
        }
    ) { padding ->
        MainContent(
            padding = padding,
            userGuideVisible = userGuideVisible.value,
            homeViewModel = homeViewModel,
            navigateToSearch = navigateToSearch,
            navigateToSearchWitImage = navigateToSearchWitImage,
            albumListSheetState = albumListSheetState,
            onOpenIndexAlbums = onOpenIndexAlbums,
            onOpenSearchRange = { showSearchRangeBottomSheet = true },
            onOpenSearchConfig = { showSearchFilterBottomSheet = true },
            navigateToSimilar = navigateToSimilar,
            navigateToSetting = navigateToSetting
        )
    }

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

@Composable
private fun MainContent(
    padding: PaddingValues,
    userGuideVisible: Boolean,
    homeViewModel: HomeViewModel,
    navigateToSearch: (String) -> Unit,
    navigateToSearchWitImage: (Uri) -> Unit,
    albumListSheetState: AppBottomSheetState,
    onOpenIndexAlbums: () -> Unit,
    onOpenSearchRange: () -> Unit,
    onOpenSearchConfig: () -> Unit,
    navigateToSimilar: () -> Unit,
    navigateToSetting: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        verticalArrangement = if (userGuideVisible) Arrangement.Center else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SearchSection(
            userGuideVisible = userGuideVisible,
            homeViewModel = homeViewModel,
            navigateToSearch = navigateToSearch,
            navigateToSearchWitImage = navigateToSearchWitImage,
            onOpenIndexAlbums = onOpenIndexAlbums,
            onOpenSearchRange = onOpenSearchRange,
            onOpenSearchConfig = onOpenSearchConfig,
            navigateToSimilar = navigateToSimilar,
            navigateToSetting = navigateToSetting
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
    navigateToSearchWitImage: (Uri) -> Unit,
    onOpenIndexAlbums: () -> Unit,
    onOpenSearchRange: () -> Unit,
    onOpenSearchConfig: () -> Unit,
    navigateToSimilar: () -> Unit,
    navigateToSetting: () -> Unit
) {
    AnimatedVisibility(visible = !userGuideVisible) {
        Column(
            modifier = Modifier.fillMaxSize(),
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

            Spacer(modifier = Modifier.size(24.dp))

            QuickActionsSection(
                onOpenIndexAlbums = onOpenIndexAlbums,
                onOpenSearchRange = onOpenSearchRange,
                onOpenSearchConfig = onOpenSearchConfig,
                navigateToSimilar = navigateToSimilar,
                navigateToSetting = navigateToSetting
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
    onOpenIndexAlbums: () -> Unit,
    onOpenSearchRange: () -> Unit,
    onOpenSearchConfig: () -> Unit,
    navigateToSimilar: () -> Unit,
    navigateToSetting: () -> Unit
) {
    TopAppBar(
        title = {
            LogoText(size = 20f)
        },
        actions = {
            IconButton(onClick = onClickHelpButton) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = "Help"
                )
            }
        }
    )
}

@Composable
private fun QuickActionsSection(
    onOpenIndexAlbums: () -> Unit,
    onOpenSearchRange: () -> Unit,
    onOpenSearchConfig: () -> Unit,
    navigateToSimilar: () -> Unit,
    navigateToSetting: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.quick_actions_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton(
                title = stringResource(R.string.menu_index_albums_short),
                onClick = onOpenIndexAlbums
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            QuickActionButton(
                title = stringResource(R.string.similar_photos_short),
                onClick = navigateToSimilar
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_similar),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            QuickActionButton(
                title = stringResource(R.string.menu_search_range_short),
                onClick = onOpenSearchRange
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            QuickActionButton(
                title = stringResource(R.string.menu_settings),
                onClick = navigateToSetting
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

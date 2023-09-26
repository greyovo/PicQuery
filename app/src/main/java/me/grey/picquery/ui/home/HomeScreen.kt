package me.grey.picquery.ui.home

import LogoRow
import SearchInput
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.common.Constants
import me.grey.picquery.common.InitializeEffect
import me.grey.picquery.common.calculateRemainingTime
import me.grey.picquery.common.showToast
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.ui.albums.IndexingAlbumState
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import rememberAppBottomSheetState

@OptIn(InternalTextApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject(),
    navigateToSearch: (String) -> Unit,
    navigateToSetting: () -> Unit,
) {
    val initialized = remember { mutableStateOf(false) }

    // === BottomSheet block
    val albumListSheetState = rememberAppBottomSheetState()
    AddAlbumBottomSheet(sheetState = albumListSheetState)
    // === BottomSheet end

    // === Permission handling block
    val scope = rememberCoroutineScope()
    val mediaPermissions =
        rememberMultiplePermissionsState(
            permissions = Constants.PERMISSIONS,
            onPermissionsResult = { scope.launch { albumManager.initAllAlbumList() } },
        )
    InitializeEffect {
        if (!mediaPermissions.allPermissionsGranted) {
            mediaPermissions.launchMultiplePermissionRequest()
        } else {
            albumManager.initAllAlbumList()
        }
    }
    // === Permission handling end

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            val busyToastText = stringResource(R.string.busy_when_add_album_toast)
            TextButton(
                onClick = {
                    if (albumManager.isEncoderBusy) {
                        showToast(busyToastText)
                    } else {
                        scope.launch { albumListSheetState.show() }
                    }
                },
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Default.Photo, contentDescription = "")
                    Box(modifier = Modifier.width(5.dp))
                    Text(text = stringResource(R.string.index_album_btn))

                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = { EncodingProgressBar() },
    ) { it ->
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxHeight(0.7f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoRow(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            SearchInput(
                queryText = remember { viewModel.searchText },
                onStartSearch = { text ->
                    if (text.isNotEmpty()) {
                        viewModel.searchText.value = text
                        navigateToSearch(text)
                    }
                },
            )
        }
    }

}


@Composable
private fun EncodingProgressBar(
    albumManager: AlbumManager = koinInject(),
) {
    val state by remember { albumManager.indexingAlbumState }
    var progress = (state.current.toDouble() / state.total).toFloat()
    if (progress.isNaN()) progress = 0.0f
    val finished = state.status == IndexingAlbumState.Status.Finish

    fun onClickOk() {
        albumManager.clearIndexingState()
    }

    AnimatedVisibility(visible = state.status != IndexingAlbumState.Status.None) {
        BottomAppBar {
            Column(
                Modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.indexing_progress,
                            state.current,
                            state.total
                        )
                    )
                    val remain = calculateRemainingTime(
                        state.current,
                        state.total,
                        state.cost
                    )
                    TextButton(
                        onClick = { onClickOk() },
                        enabled = finished
                    ) {
                        Text(
                            text = if (finished) stringResource(R.string.finish_button)
                            else stringResource(R.string.estimate_remain_time) +
                                    " ${DateUtils.formatElapsedTime(remain)}"
                        )
                    }
                }
                Box(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress,
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}
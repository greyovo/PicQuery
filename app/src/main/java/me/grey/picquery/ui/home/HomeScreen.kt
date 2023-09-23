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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.grey.picquery.R
import me.grey.picquery.common.Constants
import me.grey.picquery.common.calculateRemainingTime
import me.grey.picquery.ui.albums.AlbumViewModel
import me.grey.picquery.ui.albums.IndexingAlbumState

@OptIn(InternalTextApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    navigateToSearch: (String) -> Unit,
    onInitAllAlbumList: () -> Unit,
    navigateToSetting: () -> Unit,
    onManageAlbum: () -> Unit,
    onSelectSearchTarget: () -> Unit,
    onSelectSearchRange: () -> Unit,
) {
    /* === Permission handling block === */
    val mediaPermissions =
        rememberMultiplePermissionsState(
            permissions = Constants.PERMISSIONS,
            onPermissionsResult = { onInitAllAlbumList() },
        )
    LaunchedEffect(Unit) {
        if (!mediaPermissions.allPermissionsGranted) {
            mediaPermissions.launchMultiplePermissionRequest()
        } else {
            onInitAllAlbumList()
        }
    }
    /* === End of permission handling  === */

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            TextButton(
                onClick = { onManageAlbum() },
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Default.Photo, contentDescription = "")
                    Box(modifier = Modifier.width(5.dp))
                    Text(text = "索引相册")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
//            MediumTopAppBar(
//                title = { LogoRow() },
//                scrollBehavior = scrollBehavior,
//                colors = TopAppBarDefaults.mediumTopAppBarColors(
//                    scrolledContainerColor = MaterialTheme.colorScheme.background
//                ),
//                actions = { TopBarActions() },
//            )
        },
//        bottomBar = { BottomEncodingProgressBar() }
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
                onStartSearch = { text -> navigateToSearch(text) },
                onSelectSearchTarget = onSelectSearchTarget,
                onSelectSearchRange = onSelectSearchRange,
            )
        }
    }

}


@Composable
private fun BottomEncodingProgressBar(
    albumViewModel: AlbumViewModel = viewModel(),
) {
    val state by remember { albumViewModel.indexingAlbumState }
    var progress = (state.current.toDouble() / state.total).toFloat()
    if (progress.isNaN()) progress = 0.0f
    val finished = state.status == IndexingAlbumState.Status.Finish

    fun onClickOk() {
        albumViewModel.clearIndexingState()
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
package me.grey.picquery.ui.search

import LogoRow
import SearchInput
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.grey.picquery.R
import me.grey.picquery.common.calculateRemainingTime
import me.grey.picquery.ui.albums.AddAlbumBottomSheet
import me.grey.picquery.ui.albums.AlbumViewModel

@OptIn(InternalTextApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { LogoRow() },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                actions = { TopBarActions() },
            )
        },
        bottomBar = { BottomEncodingProgressBar() }
    ) {
        Column(
            modifier = Modifier.padding(it),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            SearchInput()
            SearchResultGrid()
        }
    }

    // BottomSheet or Dialogs
    AddAlbumBottomSheet()
    SearchFilterBottomSheet()
}

@Composable
private fun TopBarActions(
    albumViewModel: AlbumViewModel = viewModel(),
    searchViewModel: SearchViewModel = viewModel()
) {
    val size = Modifier.size(22.dp)
    IconButton(onClick = {
        albumViewModel.openBottomSheet()
    }) {
        Icon(
            modifier = size,
            imageVector = Icons.Default.AddCircle,
            contentDescription = null
        )
    }
    IconButton(onClick = { searchViewModel.openFilterBottomSheet() }) {
        Icon(
            modifier = size,
            imageVector = Icons.Rounded.FilterList,
            contentDescription = null
        )
    }
    IconButton(onClick = { /*TODO*/ }) {
        Icon(
            modifier = size,
            imageVector = Icons.Default.MoreVert,
            contentDescription = null
        )
    }
}

@Composable
private fun BottomEncodingProgressBar(
    albumViewModel: AlbumViewModel = viewModel(),
) {
    val state by remember { albumViewModel.encodingAlbumState }
    val progress = (state.current.toDouble() / state.total).toFloat()

    fun onClickOk() {
        albumViewModel.closeProgressBar()
    }

    AnimatedVisibility(visible = state.total >= 1) {
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
                    Text(text = stringResource(R.string.indexing_progress, state.current, state.total))
                    val remain = calculateRemainingTime(
                        state.current,
                        state.total,
                        state.cost
                    )
                    val finished = state.current == state.total && state.total >= 1
                    TextButton(
                        onClick = { onClickOk() },
                        enabled = finished
                    ) {
                        Text(
                            text = if (finished) stringResource(R.string.finish_button)
                            else DateUtils.formatElapsedTime(remain)
                        )
                    }
                }
                Box(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    if (progress.isNaN()) 0.0f else progress,
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}


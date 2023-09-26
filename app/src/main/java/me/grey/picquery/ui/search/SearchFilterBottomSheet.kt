package me.grey.picquery.ui.search

import AppBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.core.ImageSearcher
import me.grey.picquery.ui.albums.AlbumViewModel
import me.grey.picquery.ui.home.EmptyAlbumTips
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterBottomSheet(
    sheetState: AppBottomSheetState,
    searchViewModel: SearchViewModel = koinInject(),
    albumViewModel: AlbumViewModel = koinInject(),
    imageSearcher: ImageSearcher = koinInject()
) {
    val open by rememberSaveable { searchViewModel.isFilterOpen }
//    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch {
            sheetState.hide()
        }
    }

    if (sheetState.isVisible) {
        ModalBottomSheet(
            onDismissRequest = { close() },
            sheetState = sheetState.sheetState,
        ) {
            val list = remember { albumViewModel.searchableAlbumList }
            val selectedList = remember { searchViewModel.searchRange }
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.search_range_selection_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                supportingContent = {
                    Text(text = stringResource(R.string.search_range_selection_subtitle))
                },
                trailingContent = {
                    Button(onClick = { close() }) {
                        Text(text = stringResource(R.string.finish_button))
                    }
                }
            )

            val searchRangeAll = remember { searchViewModel.isSearchRangeAll }
            ListItem(
                modifier = Modifier.clickable { searchRangeAll.value = !searchRangeAll.value },
                headlineContent = { Text(text = stringResource(R.string.all_albums)) },
                trailingContent = {
                    Switch(
                        checked = searchRangeAll.value,
                        onCheckedChange = {
                            searchViewModel.toggleSearchAll()
                        },
                    )
                }
            )

            if (list.isEmpty()) {
                EmptyAlbumTips(onClose = { close() })
            } else {
                Box(modifier = Modifier.padding(bottom = 55.dp)) {
                    SearchAbleAlbums(enabled = !searchRangeAll.value)
                }
            }
        }
    }
}

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
private fun SearchAbleAlbums(
    enabled: Boolean,
    albumViewModel: AlbumViewModel = koinInject(),
    searchViewModel: SearchViewModel = koinInject()
) {
    val searchRange = remember { searchViewModel.searchRange }
    val all = remember { albumViewModel.searchableAlbumList }
    FlowRow(
        Modifier.padding(horizontal = 12.dp)
    ) {
        repeat(all.size) { index ->
            val album = all[index]
            val selected = searchRange.contains(album)

            val colors = FilterChipDefaults.elevatedFilterChipColors(
                iconColor = MaterialTheme.colorScheme.primary,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            )

            ElevatedFilterChip(
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 6.dp),
                colors = colors,
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = if (selected) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.AddCircleOutline
                        },
                        contentDescription = "",
                    )
                },
                selected = selected,
                onClick = { if (enabled) searchViewModel.toggleToRange(album) },
                label = { Text(text = "${album.label} (${album.count})") }
            )
        }
    }
}
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.data.model.Album
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher
import me.grey.picquery.ui.home.EmptyAlbumTips
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterBottomSheet(
    sheetState: AppBottomSheetState,
    imageSearcher: ImageSearcher = koinInject(),
    albumManager: AlbumManager = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val candidates = remember { albumManager.searchableAlbumList }
    val selectedList = remember { mutableStateListOf<Album>() }
    selectedList.addAll(imageSearcher.searchRange.toList())
    val searchAll = remember { mutableStateOf(imageSearcher.isSearchAll.value) }

    val canSave = remember {
        derivedStateOf { searchAll.value || selectedList.isNotEmpty() }
    }

    fun closeFilter() {
        scope.launch { sheetState.hide() }
    }

    fun saveFilter() {
        scope.launch {
            imageSearcher.updateRange(selectedList, searchAll.value)
            sheetState.hide()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { closeFilter() },
        sheetState = sheetState.sheetState,
    ) {
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
                Button(
                    onClick = { saveFilter() },
                    enabled = canSave.value
                ) {
                    Text(text = stringResource(R.string.finish_button))
                }
            }
        )

        ListItem(
            modifier = Modifier.clickable { searchAll.value = !searchAll.value },
            headlineContent = { Text(text = stringResource(R.string.all_albums)) },
            trailingContent = {
                Switch(
                    checked = searchAll.value,
                    onCheckedChange = { searchAll.value = it },
                )
            }
        )

        if (candidates.isEmpty()) {
            EmptyAlbumTips(onClose = { closeFilter() })
        } else {
            Box(modifier = Modifier.padding(bottom = 55.dp)) {
                SearchAbleAlbums(
                    enabled = !searchAll.value,
                    candidates = candidates,
                    selectedList = selectedList,
                    onAdd = { selectedList.add(it) },
                    onRemove = { selectedList.remove(it) },
                )
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
    candidates: List<Album>,
    selectedList: List<Album>,
    onAdd: (Album) -> Unit,
    onRemove: (Album) -> Unit,
) {

    FlowRow(
        Modifier.padding(horizontal = 12.dp)
    ) {
        repeat(candidates.size) { index ->
            val album = candidates[index]
            val selected = remember { mutableStateOf(selectedList.contains(album)) }

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
                        imageVector = if (selected.value) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.AddCircleOutline
                        },
                        contentDescription = "",
                    )
                },
                selected = selected.value,
                onClick = {
                    if (enabled) {
                        if (!selected.value) {
                            onAdd(album)
                            selected.value = true
                        } else {
                            onRemove(album)
                            selected.value = false
                        }
                    }
                },
                label = { Text(text = "${album.label} (${album.count})") }
            )
        }
    }
}
package me.grey.picquery.ui.search

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.data.model.Album
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher
import me.grey.picquery.ui.home.EmptyAlbumTips
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchRangeBottomSheet(
    imageSearcher: ImageSearcher = koinInject(),
    albumManager: AlbumManager = koinInject(),
    dismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val candidates by albumManager.searchableAlbumList.collectAsState()
    val selectedList = remember { mutableStateListOf<Album>() }
    selectedList.addAll(imageSearcher.searchRange.toList())
    var searchAll by imageSearcher.isSearchAll

    val canSave = remember {
        derivedStateOf { searchAll || selectedList.isNotEmpty() }
    }

    fun saveFilter() {
        scope.launch {
            imageSearcher.updateRange(selectedList, searchAll)
            dismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
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
            modifier = Modifier.clickable { searchAll = !searchAll },
            headlineContent = { Text(text = stringResource(R.string.all_albums)) },
            trailingContent = {
                Switch(
                    checked = searchAll,
                    onCheckedChange = { searchAll = it },
                )
            }
        )

        if (candidates.isEmpty()) {
            EmptyAlbumTips(onClose = dismiss)
        } else {
            Box(modifier = Modifier.padding(bottom = 55.dp)) {
                SearchAbleAlbums(
                    enabled = !searchAll,
                    candidates = candidates,
                    selectedList = selectedList,
                    onAdd = { selectedList.add(it) },
                    onRemove = { selectedList.remove(it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchAbleAlbums(
    enabled: Boolean,
    candidates: List<Album>,
    selectedList: List<Album>,
    onAdd: (Album) -> Unit,
    onRemove: (Album) -> Unit,
) {
    FlowRow(modifier = Modifier.padding(horizontal = 12.dp)) {
        val colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,  // 浅色背景
            labelColor = MaterialTheme.colorScheme.onSurface,  // 文字颜色
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,  // 图标颜色
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        )
        repeat(candidates.size) { index ->
            val album = candidates[index]
            val selected by remember { mutableStateOf(selectedList.contains(album)) }
            AlbumFilterChip(
                album = album,
                enabled = enabled,
                isSelected = selected,
                onAdd = onAdd,
                onRemove = onRemove,
                colors = colors
            )
        }
    }
}

@Composable
private fun AlbumFilterChip(
    album: Album,
    enabled: Boolean,
    isSelected: Boolean,
    onAdd: (Album) -> Unit,
    onRemove: (Album) -> Unit,
    colors: SelectableChipColors
) {
    var selected by remember { mutableStateOf(isSelected) }
    val context = LocalContext.current
    FilterChip(
        colors = colors,
        onClick = {
            if (enabled) {
                selected = !selected
                if (selected) onAdd(album) else onRemove(album)
            }else{
                Toast.makeText(context, "Please turn off select all first!", Toast.LENGTH_SHORT).show()
            }
        },
        label = { Text(text = "${album.label} (${album.count})") },
        selected = selected,
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Done icon",
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null,
        modifier = Modifier.padding(8.dp)
    )
}
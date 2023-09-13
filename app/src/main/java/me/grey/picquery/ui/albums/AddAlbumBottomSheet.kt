package me.grey.picquery.ui.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.data.model.Album

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlbumBottomSheet(
    albumViewModel: AlbumViewModel = viewModel()
) {
    val openBottomSheet by rememberSaveable { albumViewModel.isBottomSheetOpen }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    if (openBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { scope.launch { albumViewModel.closeBottomSheet(sheetState) } },
            sheetState = sheetState,
        ) {
            val list = remember { albumViewModel.unsearchableAlbumList }
            if (list.isEmpty()) {
                EmptyAlbumTips(
                    onClose = {
                        scope.launch { albumViewModel.closeBottomSheet(sheetState) }
                    },
                )
            } else {
                val selectedList = remember { albumViewModel.albumsToEncode }
                AlbumSelectionList(
                    list, selectedList,
                    onFinish = {
                        albumViewModel.encodeSelectedAlbums()
                        scope.launch { albumViewModel.closeBottomSheet(sheetState) }
                    },
                    onSelectAll = {
                        albumViewModel.toggleSelectAllAlbums()
                    },
                    onSelectItem = {
                        albumViewModel.toggleAlbumSelection(it)
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyAlbumTips(
    onClose: () -> Unit
) {
    Column(
        Modifier
            .height(180.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.no_more_album),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Box(modifier = Modifier.height(20.dp))
        Button(onClick = { onClose() }) {
            Text(text = stringResource(id = R.string.ok))
        }
    }
}


@Composable
fun AlbumSelectionList(
    list: List<Album>,
    selectedList: List<Album>,
    onFinish: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectItem: (Album) -> Unit,
) {
    Column {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(id = R.string.add_album),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            supportingContent = {
                Text(text = "选择需要索引的相册")
            },
            trailingContent = {
                Row {
                    TextButton(onClick = { onSelectAll() }) {
                        Text(text = if (list.size == selectedList.size) "全不选" else "全选")
                    }
                    Box(modifier = Modifier.width(5.dp))
                    Button(onClick = { onFinish() }) {
                        Text(text = "完成")
                    }
                }
            }
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.padding(horizontal = 8.dp),
        content = {
            items(
                list.size,
                key = { list[it].id },
            ) { index ->
                val selected = selectedList.contains(list[index])
                AlbumCard(
                    list[index],
                    selected = selected,
                    onItemClick = {
                        onSelectItem(list[index])
                    },
                )
            }
        }
    )
}
package me.grey.picquery.ui.search

import LogoRow
import SearchInput
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.ui.albums.AlbumCard
import me.grey.picquery.ui.main.EncodingAlbumState
import me.grey.picquery.ui.main.MainViewModel

@OptIn(InternalTextApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: SearchViewModel = viewModel(),
    mainVm: MainViewModel = viewModel(),
    paddingValues: PaddingValues,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state = remember { mainVm.encodingAlbumState }
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
        bottomBar = {
            if (state.value.album != null)
                BottomEncodingStateBar(state.value)
        }
    ) {
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(it),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
//            AlbumListBottomSheet()
            SearchInput()
            SearchResultGrid()
        }
    }

    // BottomSheet or Dialogs
    AlbumListBottomSheet()
}

@Composable
private fun TopBarActions(
    vm: SearchViewModel = viewModel()
) {
    IconButton(onClick = {
        vm.openBottomSheet()
    }) {
        Icon(
            imageVector = Icons.Default.AddCircle,
            contentDescription = null
        )
    }
    IconButton(onClick = { /*TODO*/ }) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = null
        )
    }
    IconButton(onClick = { /*TODO*/ }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = null
        )
    }
}

@Composable
private fun BottomEncodingStateBar(
    state: EncodingAlbumState
) {
    val progress =
        if (state.total >= 1) (state.current.toDouble() / state.total).toFloat()
        else 0.0f
    BottomAppBar {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp)
            ) {
                Text(text = "正在索引: ${state.album?.label ?: ""} ${state.current} / ${state.total}")
                Box(modifier = Modifier.height(15.dp))
                if (state.total >= 1) LinearProgressIndicator(
                    progress,
                    Modifier.fillMaxWidth(0.8f)
                )
            }

            TextButton(onClick = { }, enabled = progress >= 1) {
                Text(text = "OK")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumListBottomSheet(
    vm: SearchViewModel = viewModel(),
    mainVm: MainViewModel = viewModel()
) {
    val openBottomSheet by rememberSaveable { vm.isBottomSheetOpen }
    val bottomSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    if (openBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.closeBottomSheet() },
            sheetState = bottomSheetState,
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
                            TextButton(onClick = { /*TODO*/ }) {
                                Text(text = "全选")
                            }
                            Box(modifier = Modifier.width(5.dp))
                            Button(onClick = { /*TODO*/ }) {
                                Text(text = "完成")
                            }
                        }
                    }
                )
//                Row(
//                    Modifier
//                        .fillMaxWidth()
//                        .padding(vertical = 10.dp, horizontal = 14.dp),
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Text(
//                        text = stringResource(id = R.string.add_album),
//                        style = MaterialTheme.typography.headlineSmall
//                    )
//                    ElevatedButton(onClick = { /*TODO*/ }) {
//                        Text(text = "完成")
//                    }
//                }
            }

            val list = remember { mainVm.unsearchableAlbumList }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
                content = {
                    items(
                        list.size,
                        key = { list[it].id },
                    ) { index ->
                        AlbumCard(list[index], onItemClick = {
                            scope.launch {
                                mainVm.encodeAlbum(list[index])
                                bottomSheetState.hide()
                            }.invokeOnCompletion {
                                vm.isBottomSheetOpen.value = false
                            }
                        })
                    }
                }
            )
        }
    }
}
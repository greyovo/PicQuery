package me.grey.picquery.ui.indexmr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.data.model.Album
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.ui.common.BackButton
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val FlagAlbumStatusNormal = 0
val FlagAlbumStatusInvalid = 1
val FlagAlbumStatusUpdateNeeded = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexMgrScreen(
    onNavigateBack: () -> Unit,
    albumManager: AlbumManager = koinInject(),
) {

    val indexedAlbum = albumManager.searchableAlbumList.collectAsState().value.toMutableStateList()
    val allAlbum = albumManager.getAlbumList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("索引管理器(点击删除对应索引)") },
                navigationIcon = { BackButton { onNavigateBack() } },
            )
        },
        modifier = Modifier.padding(horizontal = 5.dp)
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            repeat(indexedAlbum.size) { index ->
                val album = indexedAlbum[index]
                if (allAlbum.any { a -> a.id == album.id }) {
                    val albumNow = allAlbum.find{ item -> item.id == album.id }
                    if (albumNow!!.count != album.count || albumNow!!.timestamp != album.timestamp) {
                        item { AlbumItem(indexedAlbum, album, albumManager, FlagAlbumStatusUpdateNeeded) }
                    } else {
                        item { AlbumItem(indexedAlbum, album, albumManager, FlagAlbumStatusNormal) }
                    }
                } else {
                    item{ AlbumItem(indexedAlbum, album, albumManager, FlagAlbumStatusInvalid) }
                }

            }
        }
    }
}

@Composable
private fun AlbumItem(indexedAlbum: SnapshotStateList<Album>, album: Album, albumManager: AlbumManager, albumStatusEnum: Int) {
    var isLoading by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)
    val dateStr = dateFmt.format(Date(album.timestamp as Long * 1000))
    var descString = "图片数：${album.count} 日期：${dateStr}"
    if (albumStatusEnum == FlagAlbumStatusInvalid){
        descString += "\n  此相册似乎无效"
    } else if (albumStatusEnum == FlagAlbumStatusUpdateNeeded) {
        descString += "\n  此相册似乎需要更新"
    }
    val scope = rememberCoroutineScope()
    ListItem(
        leadingContent = {
            if (albumStatusEnum == FlagAlbumStatusInvalid){
                Icon(
                    imageVector = Icons.Filled.NoPhotography,
                    contentDescription = "This album seems to be invalid, click to delete the index for this album"
                )
            } else if (albumStatusEnum == FlagAlbumStatusUpdateNeeded) {
                Icon(
                    imageVector = Icons.Filled.SyncProblem,
                    contentDescription = "This album seems need update, click to delete the index for this album"
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PhotoAlbum,
                    contentDescription = "Click to delete the index for this album"
                )
            }
        },
        headlineContent = { Text(text = album.label) },
        supportingContent = {
            if (isDone) {
                Text(text = "此项目的索引已删除")
            } else if (isLoading) {
                Text(text = "请稍后")
            } else {
                Text(text = descString)
            }
        },
        modifier = Modifier.clickable (enabled = !isLoading && !isDone) {
            if (indexedAlbum.any { i -> i.id == album.id }) {
                isLoading = true
                scope.launch {
                    removeIndexByAlbum( album, albumManager)
                }.invokeOnCompletion {
                    isDone = true
                }
            }
        }
    )
}

private suspend fun removeIndexByAlbum ( album: Album, albumManager: AlbumManager){
    withContext(Dispatchers.IO) {
        albumManager.removeSingleAlbumIndex(album)
        albumManager.initDataFlow()
    }
}
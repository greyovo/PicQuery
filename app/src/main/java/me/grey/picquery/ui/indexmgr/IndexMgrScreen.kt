package me.grey.picquery.ui.indexmgr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.R
import me.grey.picquery.data.model.Album
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.ui.common.BackButton
import org.koin.compose.koinInject

const val FlagAlbumStatusNormal = 0
const val FlagAlbumStatusInvalid = 1
const val FlagAlbumStatusUpdateNeeded = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexMgrScreen(onNavigateBack: () -> Unit, albumManager: AlbumManager = koinInject()) {
    val indexedAlbum = albumManager.searchableAlbumList.collectAsState().value.toMutableStateList()
    val allAlbum = albumManager.getAlbumList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.index_mgr_title)) },
                navigationIcon = { BackButton { onNavigateBack() } }
            )
        },
        modifier = Modifier.padding(horizontal = 5.dp)
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            repeat(indexedAlbum.size) { index ->
                val album = indexedAlbum[index]
                if (allAlbum.any { a -> a.id == album.id }) {
                    val albumNow = allAlbum.find { item -> item.id == album.id }
                    if (albumNow!!.count != album.count || albumNow.timestamp != album.timestamp) {
                        item {
                            AlbumItem(
                                indexedAlbum,
                                album,
                                albumManager,
                                FlagAlbumStatusUpdateNeeded
                            )
                        }
                    } else {
                        item { AlbumItem(indexedAlbum, album, albumManager, FlagAlbumStatusNormal) }
                    }
                } else {
                    item { AlbumItem(indexedAlbum, album, albumManager, FlagAlbumStatusInvalid) }
                }
            }
        }
    }
}

@Composable
private fun AlbumItem(
    indexedAlbum: SnapshotStateList<Album>,
    album: Album,
    albumManager: AlbumManager,
    albumStatusEnum: Int
) {
    var isLoading by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlbumIndexDeletionDialog(
        showDialog = showConfirmDialog,
        onDismiss = { showConfirmDialog = false },
        onConfirm = {
            showConfirmDialog = false
            if (indexedAlbum.any { it.id == album.id }) {
                isLoading = true
                scope.launch {
                    removeIndexByAlbum(album, albumManager)
                }.invokeOnCompletion {
                    isDone = true
                }
            }
        }
    )

    ListItem(
        colors = AlbumItemColors(albumStatusEnum),
        leadingContent = { AlbumItemLeadingIcon(albumStatusEnum) },
        headlineContent = { AlbumItemHeadline(album.label) },
        supportingContent = {
            AlbumItemSupportingContent(
                albumStatusEnum = albumStatusEnum,
                album = album,
                isLoading = isLoading,
                isDone = isDone
            )
        },
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                enabled = !isLoading && !isDone,
                onClick = { showConfirmDialog = true }
            )
    )
}

@Composable
private fun AlbumIndexDeletionDialog(showDialog: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.album_confirm_delete_title)) },
            text = { Text(stringResource(R.string.album_confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.album_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.album_cancel_button))
                }
            }
        )
    }
}

@Composable
private fun AlbumItemLeadingIcon(albumStatusEnum: Int) {
    val (icon, iconDescription) = when (albumStatusEnum) {
        FlagAlbumStatusInvalid -> Icons.Filled.NoPhotography to R.string.album_invalid_icon_desc
        FlagAlbumStatusUpdateNeeded -> Icons.Filled.SyncProblem to R.string.album_update_needed_icon_desc
        else -> Icons.Filled.PhotoAlbum to R.string.album_normal_icon_desc
    }

    Icon(
        imageVector = icon,
        contentDescription = stringResource(iconDescription),
        tint = when (albumStatusEnum) {
            FlagAlbumStatusInvalid -> MaterialTheme.colorScheme.error
            FlagAlbumStatusUpdateNeeded -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun AlbumItemHeadline(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun AlbumItemSupportingContent(albumStatusEnum: Int, album: Album, isLoading: Boolean, isDone: Boolean) {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateStr = dateFmt.format(Date(album.timestamp * 1000))

    val descriptionText = buildAnnotatedString {
        append("${stringResource(R.string.album_photo_count)}: ${album.count}\n")
        append("${stringResource(R.string.album_date)}: $dateStr\n")

        when (albumStatusEnum) {
            FlagAlbumStatusInvalid -> append(stringResource(R.string.album_invalid_desc))
            FlagAlbumStatusUpdateNeeded -> append(stringResource(R.string.album_update_needed_desc))
        }
    }

    when {
        isDone -> Text(
            text = stringResource(R.string.album_index_deleted),
            color = MaterialTheme.colorScheme.secondary
        )
        isLoading -> Text(
            text = stringResource(R.string.album_loading),
            color = MaterialTheme.colorScheme.tertiary
        )
        else -> Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AlbumItemColors(albumStatusEnum: Int) = ListItemDefaults.colors(
    containerColor = when (albumStatusEnum) {
        FlagAlbumStatusInvalid -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        FlagAlbumStatusUpdateNeeded -> MaterialTheme.colorScheme.tertiaryContainer.copy(
            alpha = 0.3f
        )
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
    },
    headlineColor = when (albumStatusEnum) {
        FlagAlbumStatusInvalid -> MaterialTheme.colorScheme.error
        FlagAlbumStatusUpdateNeeded -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    },
    supportingColor = when (albumStatusEnum) {
        FlagAlbumStatusInvalid -> MaterialTheme.colorScheme.onErrorContainer
        FlagAlbumStatusUpdateNeeded -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
    }
)

private suspend fun removeIndexByAlbum(
    album: Album,
    albumManager: AlbumManager,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    withContext(dispatcher) {
        albumManager.removeSingleAlbumIndex(album)
        albumManager.initDataFlow()
    }
}

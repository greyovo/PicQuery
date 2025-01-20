package me.grey.picquery.ui.display

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.R
import me.grey.picquery.common.InitializeEffect
import me.grey.picquery.data.model.Photo
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DisplayScreen(
    initialPage: Int,
    onNavigateBack: () -> Unit,
    displayViewModel: DisplayViewModel = koinViewModel()
) {
    val photoList by displayViewModel.photoList.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = 0,
        initialPageOffsetFraction = 0f,
        pageCount = { photoList.size }
    )
    InitializeEffect {
        displayViewModel.loadPhotos(initialPage)
        pagerState.scrollToPage(initialPage)
    }
    Scaffold(
        topBar = {
            if (pagerState.currentPage + 1 <= photoList.size) {
                val currentPhoto = photoList[pagerState.currentPage]
                val bgColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = 0.4f
                )
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                    title = { TopPhotoInfoBar(currentPhoto) },
                    navigationIcon = {
                        IconButton(onClick = { onNavigateBack() }) {
                            Icon(Icons.Filled.ArrowBack, null)
                        }
                    }
                )

            }

        }
    ) {
        it.apply { }
        HorizontalPager(state = pagerState) { index ->
            ZoomablePagerImage(photo = photoList[index]) {

            }
        }
    }
}

@Composable
private fun TopPhotoInfoBar(currentPhoto: Photo) {
    val hintColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val titleStyle = MaterialTheme.typography.bodyLarge
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(color = hintColor)
    val iconSize = 16.dp
    Column {
        Text(
            text = currentPhoto.label,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(iconSize),
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = hintColor
            )
            Box(modifier = Modifier.width(4.dp))
            Text(text = currentPhoto.albumLabel, style = bodyStyle)
            Box(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = hintColor
            )
            Box(modifier = Modifier.width(4.dp))
            Text(
                text = DateUtils.getRelativeDateTimeString(
                    LocalContext.current,
                    currentPhoto.timestamp * 1000,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_SHOW_TIME,
                ).toString(),
                style = bodyStyle,
            )
        }

    }
}


// TODO 标明出处
@OptIn(
    ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class,
)
@Composable
fun ZoomablePagerImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    maxScale: Float = 5f,
    onItemClick: () -> Unit
) {
    val zoomState = rememberZoomState(maxScale = maxScale)
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    val callback = {
        showDialog = false
    }

    if (showDialog) {
        openWithExternalApp(callback, photo, context)
    }
    Scaffold {
        it.apply { }
        GlideImage(
            modifier = modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onDoubleClick = {

                    },
                    onClick = onItemClick,
                    onLongClick = { showDialog = true }
                )
                .zoomable(zoomState = zoomState),
            model = File(photo.path),
            contentDescription = photo.label,
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun openWithExternalApp(
    callback:() -> Unit,
    photo: Photo,
    context: Context
) {

    AlertDialog(
        onDismissRequest = { callback() },
        title = { Text(stringResource(R.string.open_with_external_app)) },
        confirmButton = {
            Button(onClick = {
                val intent = Intent().apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(photo.uri, "image/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(Intent.createChooser(intent, "Open with External Apps"))
                callback()
            }) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            Button(onClick = { callback() }) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )

}
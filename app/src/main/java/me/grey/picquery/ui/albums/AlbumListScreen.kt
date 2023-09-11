package me.grey.picquery.ui.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.R
import me.grey.picquery.data.model.Album
import me.grey.picquery.themeM3.PicQueryThemeM3
import me.grey.picquery.ui.widgets.CentralLoadingProgressBar
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(list: List<Album>?, paddingValues: PaddingValues) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.searchable_albums)) }) },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(paddingValues),
                onClick = { /*TODO 索引更多相册*/ }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_album)
                )
            }
        }
    ) { innerPadding ->
        if (list == null) {
            CentralLoadingProgressBar()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
                content = {
                    items(
                        list.size,
                        key = { list[it].id },
                    ) { index ->
                        AlbumCard(list[index], onItemClick = {})
                    }
                }
            )
        }
    }


}

@Composable
private fun AlbumCard(
    album: Album,
    onItemClick: (Album) -> Unit,
) {
    Column(
        modifier = Modifier.padding(
            PaddingValues(
                vertical = 8.dp,
                horizontal = 8.dp
            )
        ),
    ) {
        AlbumCover(album = album, onItemClick)
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = album.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = album.count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(top = 2.dp, bottom = 6.dp)
                .padding(horizontal = 2.dp)
        )
    }
}


@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AlbumCover(
    album: Album,
    onItemClick: (Album) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    GlideImage(
        modifier = Modifier
            .aspectRatio(1f)
            .size(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = { onItemClick(album) }
            ),
        model = File(album.coverPath),
        contentDescription = album.label,
        contentScale = ContentScale.Crop,
    )
}
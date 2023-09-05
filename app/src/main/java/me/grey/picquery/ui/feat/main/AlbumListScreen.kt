package me.grey.picquery.ui.feat.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.data.model.Album
import java.io.File


@Composable
fun AlbumListScreen(list: List<Album>?) {
    if (list == null)
        CircularProgressIndicator()
    else
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
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

@Composable
fun AlbumCard(
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
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onSurface,
        )
        Text(
            text = album.count.toString(),
            style = MaterialTheme.typography.caption,
            modifier = Modifier
                .padding(top = 2.dp, bottom = 6.dp)
                .padding(horizontal = 2.dp)
        )
    }
}


@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumCover(
    album: Album,
    onItemClick: (Album) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
//    val isPressed = interactionSource.collectIsPressedAsState()
//    val radius = if (isPressed.value) 32.dp else 16.dp
//    val cornerRadius by animateDpAsState(targetValue = radius)
    GlideImage(
        modifier = Modifier
            .aspectRatio(1f)
            .size(130.dp)
//            .border(
//                width = 1.dp,
//                color = MaterialTheme.colors.primary,
//                shape = RoundedCornerShape(16.dp)
//            )
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
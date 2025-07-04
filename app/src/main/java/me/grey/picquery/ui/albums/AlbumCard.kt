package me.grey.picquery.ui.albums

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import java.io.File
import me.grey.picquery.data.model.Album

@Composable
fun AlbumCard(album: Album, selected: Boolean, onItemClick: (Album) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val padding: Dp by animateDpAsState(if (selected) 10.dp else 6.dp, label = "")

    Box(
        modifier = Modifier
            .padding(vertical = padding, horizontal = padding)
            .clip(MaterialTheme.shapes.medium)
    ) {
        Column(
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = { onItemClick(album) }
            )
        ) {
            Box {
                AlbumCover(album = album)
                Checkbox(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    checked = selected,
                    onCheckedChange = { onItemClick(album) }
                )
            }

            Text(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(horizontal = 2.dp),
                text = album.label,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
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
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AlbumCover(album: Album) {
    GlideImage(
        modifier = Modifier
            .aspectRatio(1f)
            .size(130.dp)
            .clip(MaterialTheme.shapes.medium),
        model = File(album.coverPath),
        contentDescription = album.label,
        contentScale = ContentScale.Crop
    )
}

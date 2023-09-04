package me.grey.picquery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.data.AlbumRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.ui.ui.theme.PicQueryTheme
import com.bumptech.glide.integration.compose.GlideImage
import java.io.File

class AlbumListActivity : ComponentActivity() {
    private val albumList = mutableStateListOf<Album>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAlbumList()
        setContent {
            PicQueryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    AlbumList(list = albumList)
                }
            }
        }
    }

    private fun initAlbumList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val albumRepository = AlbumRepository(contentResolver)
            val albums = albumRepository.getAlbums()
            albumList.addAll(albums)
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onItemClick: (Album) -> Unit,
) {
    Row {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            AlbumCover(album = album, onItemClick)
            Text(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .padding(horizontal = 16.dp),
                text = album.label,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onSurface,
                fontSize = 12.sp
            )
            Text(
                text = album.count.toString(),
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                fontSize = 8.sp
            )
        }
    }
}

@Composable
fun AlbumList(list: List<Album>) {
    LazyColumn {
        items(
            list.size,
            key = { list[it].id },
        ) { index ->
            AlbumCard(list[index], onItemClick = {})
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumCover(
    album: Album,
    onItemClick: (Album) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState()
    val radius = if (isPressed.value) 32.dp else 16.dp
    val cornerRadius by animateDpAsState(targetValue = radius)
    val view = LocalView.current
    GlideImage(
        modifier = Modifier
            .aspectRatio(1f)
            .size(130.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.primary,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clip(RoundedCornerShape(cornerRadius))
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
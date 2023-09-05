package me.grey.picquery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.grey.picquery.data.AlbumRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.ui.theme.PicQueryTheme
import com.bumptech.glide.integration.compose.GlideImage
import java.io.File

class AlbumListActivity : FragmentActivity() {
    private val albumList = mutableStateListOf<Album>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initAlbumList()
        setContent {
            PicQueryTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
//                    color = MaterialTheme.colors.background
                    topBar = {

                    },
                    bottomBar = {

                    },
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

@Composable
fun AlbumList(list: List<Album>) {
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
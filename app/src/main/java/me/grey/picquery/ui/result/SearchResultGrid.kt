package me.grey.picquery.ui.result

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.data.model.Photo
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun SearchResultGrid(
    contentPadding: PaddingValues,
    viewModel: SearchResultViewModel = viewModel()
) {
    val resultList by viewModel.resultList.collectAsState()
    val searching by viewModel.searchingState.collectAsState()

    if (searching) {
        CircularProgressIndicator()
    } else if (resultList.isEmpty()) {
        Text(text = "没有找到图片")
    } else {
        LazyVerticalGrid(
            modifier = Modifier.padding(horizontal = 10.dp),
            columns = GridCells.Adaptive(100.dp),
            contentPadding = contentPadding,
            content = {
                val padding = Modifier.padding(3.dp)
                item(span = { GridItemSpan(3) }) {
                    Box(padding) {
                        PhotoResultRecommend(photo = resultList[0], onItemClick = {})
                    }
                }
                if (resultList.size > 1)
                    items(
                        resultList.size - 1,
                        key = { resultList[it].id },
                    ) { index ->
                        Box(padding) {
                            PhotoResultItem(resultList[index + 1], onItemClick = {})
                        }
                    }
            }
        )
    }
}

@ExperimentalFoundationApi
@ExperimentalGlideComposeApi
@Composable
fun PhotoResultRecommend(photo: Photo, onItemClick: (photo: Photo) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    GlideImage(
        modifier = Modifier
            .aspectRatio(1.3f)
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = { onItemClick(photo) }
            ),
        model = File(photo.path),
        contentDescription = photo.label,
        contentScale = ContentScale.Crop,
    )
}

@ExperimentalFoundationApi
@ExperimentalGlideComposeApi
@Composable
fun PhotoResultItem(photo: Photo, onItemClick: (photo: Photo) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    GlideImage(
        modifier = Modifier
            .aspectRatio(1f)
            .size(240.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = { onItemClick(photo) }
            ),
        model = File(photo.path),
        contentDescription = photo.label,
        contentScale = ContentScale.Crop,
    )
}
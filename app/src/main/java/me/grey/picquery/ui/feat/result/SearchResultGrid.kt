package me.grey.picquery.ui.feat.result

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
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
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
            content = {
                items(
                    resultList.size,
                    key = { resultList[it].id },
                ) { index ->
                    PhotoResultItem(resultList[index], onItemClick = {})
                }
            }
        )
    }
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
            .clip(RoundedCornerShape(16.dp))
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
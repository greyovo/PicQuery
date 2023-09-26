package me.grey.picquery.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.R
import me.grey.picquery.data.model.Photo
import me.grey.picquery.ui.common.CentralLoadingProgressBar
import java.io.File


@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun SearchResultGrid(
    resultList: List<Photo>,
    state: SearchState,
    onClickPhoto: (Photo, Int) -> Unit,
    ) {
//    val resultList by viewModel.resultList.collectAsState()
//    val state by viewModel.searchState.collectAsState()
    when (state) {
        SearchState.NO_INDEX -> UnReadyText()
        SearchState.LOADING -> CentralLoadingProgressBar()
        SearchState.READY -> ReadyText()
        SearchState.SEARCHING -> CentralLoadingProgressBar()
        SearchState.FINISHED -> {
            if (resultList.isEmpty()) {
                NoResultText()
            } else {
                val context = LocalContext.current
                LazyVerticalGrid(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    columns = GridCells.Adaptive(100.dp),
                    content = {
                        val padding = Modifier.padding(3.dp)
                        fun onClickPhotoResult(index: Int) {
                        }

                        // 搜索结果的第一个占满一行
                        item(span = { GridItemSpan(3) }) {
                            Box(padding) {
                                PhotoResultRecommend(
                                    photo = resultList[0],
                                    onItemClick = { onClickPhotoResult(0) },
                                )
                            }
                        }
                        // 其余结果按表格展示
                        if (resultList.size > 1)
                            items(
                                resultList.size - 1,
                                key = { resultList[it].id },
                            ) { index ->
                                Box(padding) {
                                    PhotoResultItem(
                                        resultList[index + 1],
                                        onItemClick = { onClickPhotoResult(index + 1) },
                                    )
                                }
                            }
                    }
                )
            }
        }

    }
}


@Composable
private fun UnReadyText() {
    Box(
        Modifier
            .fillMaxHeight(0.7f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedButton(onClick = { /*TODO*/ }) {
            Text(text = stringResource(id = R.string.start_index_album))
        }
    }
}

@Composable
private fun ReadyText() {
    Box(
        Modifier
            .fillMaxHeight(0.7f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(R.string.ready_for_searching_tips))
    }
}

@Composable
private fun NoResultText() {
    Box(
        Modifier
            .fillMaxHeight(0.7f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(R.string.no_images_found_tips))
    }
}

@ExperimentalFoundationApi
@ExperimentalGlideComposeApi
@Composable
private fun PhotoResultRecommend(photo: Photo, onItemClick: (photo: Photo) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    GlideImage(
        modifier = Modifier
            .aspectRatio(1.3f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
private fun PhotoResultItem(photo: Photo, onItemClick: (photo: Photo) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    GlideImage(
        modifier = Modifier
            .aspectRatio(1f)
            .size(240.dp)
            .clip(RoundedCornerShape(12.dp))
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
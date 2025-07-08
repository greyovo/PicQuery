package me.grey.picquery.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import java.io.File
import me.grey.picquery.R
import me.grey.picquery.data.model.Photo
import me.grey.picquery.ui.common.CentralLoadingProgressBar
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun SearchResultGrid(
    resultList: List<Photo>,
    state: SearchState,
    resultMap: Map<Long, Double>,
    onClickPhoto: (Photo, Int) -> Unit
) {
    when (state) {
        SearchState.NO_INDEX -> UnReadyText()
        SearchState.LOADING -> CentralLoadingProgressBar()
        SearchState.READY -> ReadyText()
        SearchState.SEARCHING -> CentralLoadingProgressBar()
        SearchState.FINISHED -> {
            if (resultList.isEmpty()) {
                NoResultText()
            } else {
                LazyVerticalGrid(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    columns = GridCells.Adaptive(100.dp),
                    content = {
                        val padding = Modifier.padding(3.dp)

                        item(span = { GridItemSpan(3) }) {
                            Box(padding) {
                                PhotoResultRecommend(
                                    photo = resultList[0],
                                    onItemClick = { onClickPhoto(resultList[0], 0) }
                                )
                            }
                        }
                        if (resultList.size > 1) {
                            items(
                                resultList.size - 1,
                                key = { resultList[it].id }
                            ) { index ->
                                Timber.tag("SearchResultGrid").e("index: $index")
                                Box(padding) {
                                    val photo = resultList[index + 1]
                                    PhotoResultItem(
                                        photo,
                                        resultMap[photo.id]?.toFloat() ?: 0f,
                                        onItemClick = {
                                            Timber.tag("SearchResultGrid").e("click: $index")
                                            onClickPhoto(resultList[index + 1], index + 1)
                                        }
                                    )
                                }
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
        ElevatedButton(onClick = { }) {
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
        contentScale = ContentScale.Crop
    )
}

@ExperimentalFoundationApi
@ExperimentalGlideComposeApi
@Composable
fun PhotoResultItem(
    photo: Photo,
    similarity: Float,
    onItemClick: (photo: Photo) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clickable { onItemClick(photo) }) {
        Column {
            Box(modifier = Modifier.aspectRatio(1f)) {
                GlideImage(
                    model = photo.uri,
                    contentDescription = "Search Result",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                // Confidence tag implementation needed
//                ConfidenceTag(
//                    confidenceLevel = 0f,
//                    modifier = Modifier
//                        .align(Alignment.TopEnd)
//                        .padding(8.dp)
//                )
            }

            // Optional: Similarity score text
//            Text(
//                text = "Similarity: ${String.format("%.2f", similarity)}",
//                style = MaterialTheme.typography.bodySmall,
//                modifier = Modifier.padding(top = 4.dp)
//            )
        }
    }
}

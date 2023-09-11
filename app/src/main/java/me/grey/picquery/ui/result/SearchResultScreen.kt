package me.grey.picquery.ui.result

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.data.model.Photo
import me.grey.picquery.themeM3.PicQueryThemeM3
import me.grey.picquery.ui.widgets.CentralLoadingProgressBar
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreen(onBack: () -> Unit) {
    PicQueryThemeM3 {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "搜索结果") },
                    navigationIcon = {
                        IconButton(onClick = { onBack() }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Return back"
                            )
                        }
                    }
                )
            },
        ) { innerPadding -> SearchResultGrid(innerPadding) }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun SearchResultGrid(
    contentPadding: PaddingValues,
    viewModel: SearchResultViewModel = viewModel()
) {
    val resultList by viewModel.resultList.collectAsState()
    val searching by viewModel.searchingState.collectAsState()

    Column(
        modifier = Modifier.padding(contentPadding)
    ) {
        SearchBarInput()
        if (searching) {
            CentralLoadingProgressBar()
        } else if (resultList.isEmpty()) {
            NoResultText()
        } else {
            LazyVerticalGrid(
                modifier = Modifier.padding(horizontal = 10.dp),
                columns = GridCells.Adaptive(100.dp),
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
}



@Composable
private fun NoResultText() {
    Box(
        Modifier
            .fillMaxHeight(0.7f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "没有找到图片")
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarInput(
    viewModel: SearchResultViewModel = viewModel()
) {
    val text = remember { viewModel.searchText }
    fun action() {
        // TODO onSearch
        Log.d("onSearch", text.value)
        viewModel.startSearch(text.value)
    }
    Box(
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        DockedSearchBar(
            modifier = Modifier.fillMaxWidth(),
            query = text.value,
            onQueryChange = { text.value = it },
            onSearch = { action() },
            active = false,
            onActiveChange = { },
            placeholder = { Text("对图片的描述...") },
            trailingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    Modifier.clickable { action() }
                )
            },
//            trailingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
        ) {}
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
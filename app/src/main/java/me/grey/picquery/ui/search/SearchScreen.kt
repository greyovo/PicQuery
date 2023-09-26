package me.grey.picquery.ui.search

import SearchInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.unit.dp
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.AlbumManager
import org.koin.compose.koinInject

@OptIn(InternalTextApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String,
    onClickPhoto: (Photo, Int) -> Unit,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    searchResult: List<Photo>,
    searchState: SearchState,
) {
    val queryText = remember { mutableStateOf(initialQuery) }
    LaunchedEffect(Unit) {
        onSearch(initialQuery)
    }
    Surface {
        Column() {
            SearchInput(
                onStartSearch = { onSearch(it) },
                queryText = queryText,
                leadingIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "back",
                        )
                    }
                }
            )
            SearchResultGrid(
                resultList = searchResult,
                state = searchState,
                onClickPhoto = onClickPhoto
            )
        }
    }
}

@Composable
private fun TopBarActions(
    albumManager: AlbumManager = koinInject(),
    searchViewModel: SearchViewModel = koinInject()
) {
    val size = Modifier.size(22.dp)
    IconButton(onClick = { albumManager.openBottomSheet() }) {
        Icon(
            modifier = size,
            imageVector = Icons.Default.AddCircle,
            contentDescription = null
        )
    }
    IconButton(onClick = { searchViewModel.openFilterBottomSheet() }) {
        Icon(
            modifier = size,
            imageVector = Icons.Rounded.FilterList,
            contentDescription = null
        )
    }
    IconButton(onClick = { /*TODO*/ }) {
        Icon(
            modifier = size,
            imageVector = Icons.Default.MoreVert,
            contentDescription = null
        )
    }
}



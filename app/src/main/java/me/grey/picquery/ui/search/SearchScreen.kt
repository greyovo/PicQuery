package me.grey.picquery.ui.search

import SearchInput
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.InternalTextApi
import me.grey.picquery.data.model.Photo
import org.koin.androidx.compose.koinViewModel

@OptIn(InternalTextApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String,
    onClickPhoto: (Photo, Int) -> Unit,
    onBack: () -> Unit,
//    onSearch: (String) -> Unit,
//    searchResult: List<Photo>,
//    searchState: SearchState,
    searchViewModel: SearchViewModel = koinViewModel()
) {
    val queryText = rememberSaveable { mutableStateOf(initialQuery) }
    val resultList = searchViewModel.resultList.collectAsState()
    val searchState = searchViewModel.searchState.collectAsState()

    val initialQueryDone = rememberSaveable { mutableStateOf(false) }
    if (!initialQueryDone.value) {
        LaunchedEffect(Unit) {
            searchViewModel.startSearch(initialQuery)
            initialQueryDone.value = true
        }
    }

    Surface {
        Column() {
            SearchInput(
                onStartSearch = { searchViewModel.startSearch(it) },
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
                resultList = resultList.value,
                state = searchState.value,
                onClickPhoto = onClickPhoto
            )
        }
    }
}



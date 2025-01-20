package me.grey.picquery.ui.search

import SearchInput
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.InternalTextApi
import androidx.core.net.toUri
import me.grey.picquery.data.model.Photo
import org.koin.androidx.compose.koinViewModel

@OptIn(InternalTextApi::class)
@Composable
fun SearchScreen(
    initialQuery: String,
    onClickPhoto: (Photo, Int) -> Unit,
    onNavigateBack: () -> Unit,
    searchViewModel: SearchViewModel = koinViewModel()
) {
    Log.d("SearchScreen", "initialQuery: $initialQuery")
    
    val resultList = searchViewModel.resultList.collectAsState()
    val searchState by searchViewModel.searchState.collectAsState()
    val initialQueryDone = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        searchViewModel.onQueryChange(initialQuery)
    }
    val queryText by searchViewModel.searchText.collectAsState()

    LaunchedEffect(queryText) {
        if (!initialQueryDone.value && queryText.isNotEmpty()) {
            if (queryText.startsWith("content")) {
                searchViewModel.startSearch(queryText.toUri())
                searchViewModel.onQueryChange("")
            } else {
                searchViewModel.startSearch(queryText)
            }
            initialQueryDone.value = true
        }
    }

    Surface {
        Column {
            SearchInput(
                onStartSearch = { searchViewModel.startSearch(it) },
                onImageSearch = { searchViewModel.startSearch(it) },
                queryText = queryText,
                onNavigateBack = onNavigateBack,
                onQueryChange = { searchViewModel.onQueryChange(it) },
                showBackButton = searchState == SearchState.FINISHED,
            )
            SearchResultGrid(
                resultList = resultList.value,
                state = searchState,
                onClickPhoto = onClickPhoto
            )
        }
    }
}



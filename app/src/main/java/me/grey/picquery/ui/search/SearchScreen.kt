package me.grey.picquery.ui.search

import LogoRow
import SearchInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.grey.picquery.ui.main.MainViewModel

@OptIn(InternalTextApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
) {
    Column(
        modifier = Modifier.padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Box(Modifier.height(20.dp))
        LogoRow()
        SearchInput()
        SearchResultGrid()
    }
}


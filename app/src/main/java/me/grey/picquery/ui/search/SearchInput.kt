import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.domain.ImageSearcher
import me.grey.picquery.domain.SearchTarget
import me.grey.picquery.ui.search.SearchFilterBottomSheet
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@InternalTextApi
@Composable
fun SearchInput(
    queryText: MutableState<String>,
    leadingIcon: @Composable (() -> Unit) = {
        Icon(
            Icons.Default.Search,
            contentDescription = null
        )
    },
    onStartSearch: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    fun searchAction() {
        onStartSearch(queryText.value)
        keyboard?.hide()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        SearchBar(
            query = queryText.value,
            onQueryChange = { queryText.value = it },
            onSearch = { searchAction() },
            active = false,
            onActiveChange = { },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = leadingIcon,
            trailingIcon = {
                if (queryText.value.isNotEmpty()) {
                    IconButton(onClick = {
                        queryText.value = ""
                        keyboard?.show()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
        ) {
        }

        val textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchTargetChip(textStyle)
            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 15.dp)
            )
            SearchRangeChip(textStyle = textStyle)
        }
    }
}

@Composable
fun SearchTargetChip(textStyle: TextStyle, imageSearcher: ImageSearcher = koinInject()) {
    var searchTargetDropdownExpanded by remember { mutableStateOf(false) }
    val currentTarget = imageSearcher.searchTarget.value

    AssistChip(
        border = null,
        onClick = { searchTargetDropdownExpanded = true },
        label = {
            Text(
                text = stringResource(id = currentTarget.labelResId),
                style = textStyle
            )
        },
        leadingIcon = {
            Icon(
                imageVector = currentTarget.icon,
                contentDescription = null
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null
            )
        }
    )

    SearchTargetDropdown(
        searchTargetDropdownExpanded,
        onDismissRequest = { searchTargetDropdownExpanded = false },
        onSelectItem = { target ->
            imageSearcher.updateTarget(target)
            searchTargetDropdownExpanded = false
        }
    )
}

@Composable
fun SearchRangeChip(textStyle: TextStyle, imageSearcher: ImageSearcher = koinInject()) {
    val sheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    val range = remember { imageSearcher.searchRange }
    val searchAll = remember { imageSearcher.isSearchAll }

    val rangeText = if (range.isEmpty() || searchAll.value) {
        stringResource(id = R.string.all_albums)
    } else {
        range.joinToString { it.label }
    }

    AssistChip(
        modifier = Modifier.fillMaxWidth(),
        border = null,
        // TODO
        onClick = { scope.launch { sheetState.show() } },
        label = {
            Text(
                text = rangeText,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null
            )
        }
    )

    if (sheetState.isVisible) {
        SearchFilterBottomSheet(sheetState)
    }
}

@Composable
fun SearchTargetDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSelectItem: (SearchTarget) -> Unit,
) {
    val targets = SearchTarget.values()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        for (item in targets) {
            DropdownMenuItem(
                text = { Text(stringResource(id = item.labelResId)) },
                onClick = { onSelectItem(item) },
                leadingIcon = {
                    Icon(
                        item.icon,
                        contentDescription = item.name
                    )
                },
            )
        }
    }
}
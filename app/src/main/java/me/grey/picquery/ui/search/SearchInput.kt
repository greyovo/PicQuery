import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextStyle
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
    leadingIcon: @Composable () -> Unit = {
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

    val textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        query = queryText.value,
        onQueryChange = { queryText.value = it },
        onSearch = { searchAction() },
        active = false,
        onActiveChange = { },
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = leadingIcon,
        trailingIcon = {
            Row {
                // Clear text button
                if (queryText.value.isNotEmpty()) {
                    IconButton(onClick = {
                        queryText.value = ""
                        keyboard?.show()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
                // More Menu button
                SearchSettingButton(textStyle)
            }
        },
    ) {
    }
}


@Composable
private fun SearchSettingButton(textStyle: TextStyle, imageSearcher: ImageSearcher = koinInject()) {
    val filterBottomSheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    val menuDropdownOpen = remember { mutableStateOf(false) }

    fun onClick() {
        // TODO 加入了OCR识别功能后再启用dropdown菜单
//        menuDropdownOpen.value = true
        scope.launch { filterBottomSheetState.show() }
    }

    IconButton(onClick = { onClick() }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = null,
            tint = textStyle.color
        )
    }

    if (filterBottomSheetState.isVisible) {
        SearchFilterBottomSheet(filterBottomSheetState)
    }

    if (menuDropdownOpen.value) {
        SearchSettingDropdown(
            menuDropdownOpen.value,
            onDismissRequest = { menuDropdownOpen.value = false },
            selectedTarget = imageSearcher.searchTarget.value,
            onChangeTarget = { target ->
                imageSearcher.updateTarget(target)
                menuDropdownOpen.value = false
            },
            enableFilter = imageSearcher.isSearchAll.value,
            onOpenFilter = {
                scope.launch { filterBottomSheetState.show() }
                menuDropdownOpen.value = false
            }
        )
    }
}

@Composable
fun SearchSettingDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    enableFilter: Boolean,
    selectedTarget: SearchTarget,
    onChangeTarget: (SearchTarget) -> Unit,
    onOpenFilter: () -> Unit,
) {
    val targets = SearchTarget.values()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        for (item in targets) {
            val selected = selectedTarget == item
            val color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            }
            DropdownMenuItem(
                text = { Text(stringResource(id = item.labelResId), color = color) },
                onClick = { onChangeTarget(item) },
                leadingIcon = {
                    Icon(
                        item.icon, contentDescription = item.name,
                        tint = color,
                    )
                },
                trailingIcon = {
                    if (selected)
                        Icon(
                            imageVector = Icons.Default.Check, contentDescription = null,
                            tint = color,
                        )
                }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.search_range_selection_title)) },
            onClick = { onOpenFilter() },
            leadingIcon = {
                Icon(
                    imageVector = if (enableFilter) {
                        Icons.Outlined.FilterAltOff
                    } else {
                        Icons.Default.FilterAlt
                    },
                    contentDescription = null,
                    tint = if (enableFilter) {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            },
        )
    }
}
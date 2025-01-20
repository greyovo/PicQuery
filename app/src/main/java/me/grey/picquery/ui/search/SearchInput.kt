import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.domain.SearchTarget
import me.grey.picquery.ui.search.SearchFilterBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@InternalTextApi
@Composable
fun SearchInput(
    modifier: Modifier = Modifier,
    queryText: String,
    onStartSearch: (String) -> Unit,
    onImageSearch: (Uri) -> Unit,
    onQueryChange: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    showBackButton: Boolean = false,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

    SearchBar(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        query = queryText,
        onQueryChange = { onQueryChange(it) },
        onSearch = { 
            onStartSearch(queryText)
            keyboard?.hide()
        },
        active = false,
        onActiveChange = { },
        placeholder = { SearchPlaceholder() },
        leadingIcon = { 
            if (showBackButton && onNavigateBack != null) {
                BackButton(onClick = onNavigateBack)
            } else {
                SearchLeadingIcon()
            }
        },
        trailingIcon = {
            SearchTrailingIcons(
                queryText = queryText,
                textStyle = textStyle,
                onClearText = {
                    onQueryChange("")
                    keyboard?.show()
                },
                onImageSearch = onImageSearch
            )
        },
    ) {}
}

@Composable
private fun SearchPlaceholder() {
    Text(stringResource(R.string.search_placeholder))
}

@Composable
private fun SearchLeadingIcon() {
    Icon(
        Icons.Default.Search,
        contentDescription = ""
    )
}

@Composable
private fun SearchTrailingIcons(
    queryText: String,
    textStyle: TextStyle,
    onClearText: () -> Unit,
    onImageSearch: (Uri) -> Unit,
) {
    Row {
        // Clear text button
        if (queryText.isNotEmpty()) {
            ClearTextButton(onClearText)
        }
        // Search settings
        SearchSettingsSection(
            textStyle = textStyle,
            onImageSearch = onImageSearch
        )
    }
}

@Composable
private fun ClearTextButton(onClear: () -> Unit) {
    IconButton(onClick = onClear) {
        Icon(
            Icons.Default.Clear,
            contentDescription = "CLEAR"
        )
    }
}

@Composable
private fun SearchSettingsSection(
    textStyle: TextStyle,
    onImageSearch: (Uri) -> Unit,
) {
    val filterSheetState = rememberAppBottomSheetState()
    val scope = rememberCoroutineScope()
    
    // Photo picker setup
    val photoPicker = rememberImagePicker(onImageSearch)
    
    // UI Components
    Row {
        ImageSearchButton(
            textStyle = textStyle,
            onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
        
        MoreOptionsButton(
            textStyle = textStyle,
            onClick = {
                scope.launch { filterSheetState.show() }
            }
        )
    }
    
    // Bottom sheet
    if (filterSheetState.isVisible) {
        SearchFilterBottomSheet(filterSheetState)
    }
}

@Composable
private fun rememberImagePicker(
    onImageSearch: (Uri) -> Unit
) = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
        Log.d("PhotoPicker", "Selected URI: $uri")
        onImageSearch(uri)
    } else {
        Log.d("PhotoPicker", "No media selected")
    }
}

@Composable
private fun ImageSearchButton(
    textStyle: TextStyle,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = "",
            tint = textStyle.color
        )
    }
}

@Composable
private fun MoreOptionsButton(
    textStyle: TextStyle,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "",
            tint = textStyle.color
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.ArrowBack,
            contentDescription = "",
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SearchTargetOptions(
    targets: Array<SearchTarget>,
    selectedTarget: SearchTarget,
    onChangeTarget: (SearchTarget) -> Unit
) {
    for (item in targets) {
        val selected = selectedTarget == item
        val color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        }
        
        SearchTargetItem(
            target = item,
            selected = selected,
            color = color,
            onClick = { onChangeTarget(item) }
        )
    }
}

@Composable
private fun SearchTargetItem(
    target: SearchTarget,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(id = target.labelResId), color = color) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                target.icon,
                contentDescription = target.name,
                tint = color,
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = color,
                )
            }
        }
    )
}

@Composable
private fun FilterOption(
    enableFilter: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.search_range_selection_title)) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = if (enableFilter) Icons.Outlined.FilterAltOff else Icons.Default.FilterAlt,
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
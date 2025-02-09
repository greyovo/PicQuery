import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.grey.picquery.R
import timber.log.Timber

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
private fun rememberImagePicker(
    onImageSearch: (Uri) -> Unit
) = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
        Timber.tag("PhotoPicker").d("Selected URI: $uri")
        onImageSearch(uri)
    } else {
        Timber.tag("PhotoPicker").d("No media selected")
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
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "",
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSettingsSection(
    textStyle: TextStyle,
    onImageSearch: (Uri) -> Unit,
) {
    var showPickerBottomSheet by remember { mutableStateOf(false) }

    // System Picker
    val photoPicker = rememberImagePicker(onImageSearch)

    // External Picker
    val externalAlbumLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                onImageSearch(uri)
            }
        }
    }

    // Bottom sheet for picker selection
    if (showPickerBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPickerBottomSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_image_source),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // System Picker Option
                PickerOptionItem(
                    icon = Icons.Outlined.Image,
                    text = stringResource(R.string.system_album),
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        showPickerBottomSheet = false
                    }
                )

                // External Picker Option (Intent-based)
                PickerOptionItem(
                    icon = Icons.Outlined.Folder,
                    text = stringResource(R.string.external_album),
                    onClick = {
                        val intent = Intent(Intent.ACTION_PICK).apply {
                            type = "image/*"
                        }
                        externalAlbumLauncher.launch(intent)
                        showPickerBottomSheet = false
                    }
                )
            }
        }
    }

    // Original Image Search Button
    Row {
        ImageSearchButton(
            textStyle = textStyle,
            onClick = {
                showPickerBottomSheet = true
            }
        )
    }
}

@Composable
private fun PickerOptionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

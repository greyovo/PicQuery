import android.util.Log
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.grey.picquery.ui.search.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@InternalTextApi
@Composable
fun SearchInput(
    viewModel: SearchViewModel = viewModel()
) {
    val text = remember { viewModel.searchText }
    val keyboard = LocalSoftwareKeyboardController.current
    fun action() {
        viewModel.startSearch(text.value)
        keyboard?.hide()
    }
    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        query = text.value,
        onQueryChange = { text.value = it },
        onSearch = { action() },
        active = false,
        onActiveChange = { },
        placeholder = { Text("对图片的描述...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.value.isNotEmpty()) {
                IconButton(onClick = {
                    viewModel.clearAll()
                    keyboard?.show()
                }) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                }
            }
        },
    ) {
    }
}

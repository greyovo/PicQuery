package me.grey.picquery.ui.result

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import me.grey.picquery.theme.PicQueryTheme
import me.grey.picquery.themeM3.PicQueryThemeM3

class SearchResultActivity : FragmentActivity() {

    private val viewModel: SearchResultViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra("text")
        if (text != null) {
            viewModel.startSearch(text)
        }
        setContent {
            val searchText = remember { viewModel.searchText }
            val state = rememberTopAppBarState()
            val expand = remember { derivedStateOf { state.collapsedFraction >= 0.55f } }
            val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state)

            PicQueryThemeM3 {
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                if (expand.value)
                                    Text(text = "搜索: ${searchText.value}")
                                else
                                    Box(modifier = Modifier.padding(end = 12.dp)) { SearchBarInput() }
                            },
//                            title = { Text(text = "搜索: $searchText") },
                            scrollBehavior = scrollBehavior,
                            navigationIcon = {
                                IconButton(onClick = { this.finish() }) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowBack,
                                        contentDescription = "Return back"
                                    )
                                }
                            }
                        )
                    },
                ) { innerPadding ->
                    SearchResultGrid(innerPadding)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SearchBarInput() {
        val text = remember { viewModel.searchText }
        fun action() {
            // TODO onSearch
            Log.d("onSearch", text.value)
            viewModel.startSearch(text.value)
        }
        ProvideTextStyle(
            value = MaterialTheme.typography.bodyLarge,
        ) {
            SearchBar(
                modifier = Modifier.fillMaxWidth(),
                query = text.value,
                onQueryChange = { text.value = it },
                onSearch = { action() },
                active = false,
                onActiveChange = { },
                placeholder = { Text("对图片的描述...") },
//            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
//            trailingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
            ) {}
        }

    }
}
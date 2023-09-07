package me.grey.picquery.ui.feat.result

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import me.grey.picquery.theme.PicQueryTheme

class SearchResultActivity : ComponentActivity() {

    private val viewModel: SearchResultViewModel by viewModels()

    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra("text")
        if (text != null) {
            viewModel.startSearch(text)
        }
        setContent {
            Scaffold(
                topBar = {
                    TopAppBar(title = {
                        Text(
                            text = "搜索结果",
                            style = TextStyle(color = MaterialTheme.colors.onPrimary)
                        )
                    },)
                }
            ) {
                SearchResultGrid()
            }
        }
    }
}

@Composable
fun Greeting2(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PicQueryTheme {
        Greeting2("Android")
    }
}
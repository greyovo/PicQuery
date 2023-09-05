package me.grey.picquery.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import me.grey.picquery.ui.theme.PicQueryTheme
import me.grey.picquery.ui.widgets.NavBottomBar
import me.grey.picquery.ui.widgets.BottomItem
import me.grey.picquery.ui.widgets.SearchInput

class MainActivity : FragmentActivity() {

    companion object {
        val mBottomTabItems =
            listOf(
                BottomItem("搜索", Icons.Filled.Search, Icons.Outlined.Search),
                BottomItem("相册", Icons.Filled.Menu, Icons.Outlined.Menu),
            )
    }

    @OptIn(InternalTextApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicQueryTheme {
                var bottomSelectedIndex by remember { mutableStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavBottomBar(bottomSelectedIndex, mBottomTabItems) {
                            bottomSelectedIndex = it
                            println(bottomSelectedIndex)
                        }
                    }
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.fillMaxHeight(0.15f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val textStyle = TextStyle(fontSize = 29.sp)
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "搜索",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colors.primary
                            )
                            Text(text = "Pic", style = textStyle)
                            Text(
                                text = "Query", style = textStyle.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        SearchInput()
                        Text(
                            text = "将在以下相册中搜索",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = TextStyle(fontSize = 14.sp, color = Color.Gray)
                        )
                        AvailableAlbumList()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AvailableAlbumList() {
    LazyColumn(content = {
//        item {
//            Text(
//                text = "将在以下相册中搜索",
//                modifier = Modifier.padding(horizontal = 16.dp),
//                style = TextStyle(fontSize = 13.sp, color = Color.Gray)
//            )
//        }
        items(13) {
            ListItem(
                icon = {
                    Icon(imageVector = Icons.Filled.List, contentDescription = "")
                },
            ) {
                Text(text = "asd $it")
            }
        }
    })
}

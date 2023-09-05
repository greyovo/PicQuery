package me.grey.picquery.ui.feat.main

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.data.model.Album
import java.io.File

@OptIn(InternalTextApi::class)
@Composable
fun SearchScreen(list: List<Album>?) {
    Column(
        Modifier.padding(bottom = 56.dp), // 避开bottomBar的遮挡
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxHeight(0.15f))
        LogoRow()
        SearchInput()
        Text(
            text = "将在以下相册中搜索",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = TextStyle(fontSize = 14.sp, color = Color.Gray)
        )
        AvailableAlbumList(list) {

        }
    }
}

@Composable
private fun LogoRow() {
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
}


@InternalTextApi
@Composable
private fun SearchInput() {
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    Row {
        TextField(value = textValue,
            onValueChange = { textValue = it },
            //            label = { Text("Enter Your Name") },
            placeholder = { Text(text = "对图片的描述...") },
            singleLine = true,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(22.dp)
                ),
            keyboardActions = KeyboardActions(onDone = {
                Log.d("onDone", textValue.text)
            }, onSearch = {
                // TODO onSearch
                Log.d("onSearch", textValue.text)
            }),
            trailingIcon = {
                IconButton(onClick = { /*TODO*/ }) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "搜索")
                }
            }

        )

    }

}

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalGlideComposeApi::class,
    ExperimentalFoundationApi::class
)
@Composable
private fun AvailableAlbumList(
    list: List<Album>?,
    onItemClick: (Album) -> Unit,
) {
    if (list == null) {
        Box(modifier = Modifier.height(1.dp))
    } else {
        LazyColumn(content = {
            items(list.size) {
                val album = list[it]
                ListItem(
                    modifier = Modifier.clickable {
                        onItemClick(album)
                    },
                    icon = {
                        Box(Modifier.size(50.dp)) {
                            GlideImage(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp)),
                                model = File(album.coverPath),
                                contentDescription = album.label,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
                ) {
                    Text(text = album.label)
                }
            }
        })
    }
}
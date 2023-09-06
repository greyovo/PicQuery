package me.grey.picquery.ui.feat.main

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
fun SearchScreen(searchAbleList: List<Album>?, unsearchableList: List<Album>?) {
    Column(
        Modifier.padding(bottom = 56.dp), // 避开bottomBar的遮挡
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Box(Modifier.height(50.dp))
        LogoRow()
        SearchInput()

        AlbumList(
            searchAbleList ?: emptyList(),
            unsearchableList ?: emptyList(),
        ) {
        }
    }
}

@Composable
private fun LogoRow() {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        val textStyle = TextStyle(fontSize = 29.sp)
        Text(text = "Pic", style = textStyle)
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "搜索",
            modifier = Modifier.size(39.dp),
            tint = MaterialTheme.colors.primary
        )
        Text(
            text = "uery", style = textStyle.copy(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumList(
    searchAbleList: List<Album>,
    unsearchableList: List<Album>,
    onClickSearchable: (Album) -> Unit,
) {
    LazyColumn(content = {
        stickyHeader {
            AlbumListHeader("可搜索相册 (${searchAbleList.size})")
        }
        items(searchAbleList.size) {
            SearchableAlbum(searchAbleList[it], onClickSearchable)
        }
        stickyHeader {
            AlbumListHeader("待索引相册 (${unsearchableList.size})")
        }
        items(unsearchableList.size) {
            UnsearchableAlbum(unsearchableList[it]) {}
        }
    })
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun SearchableAlbum(album: Album, onClick: (Album) -> Unit) {
    ListItem(
        modifier = Modifier.clickable {
            onClick(album)
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

@OptIn(ExperimentalMaterialApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun UnsearchableAlbum(album: Album, onClick: (Album) -> Unit) {
    ListItem(
        modifier = Modifier.clickable {
            onClick(album)
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

@Composable
fun AlbumListHeader(title: String) {
    Surface(
        color = MaterialTheme.colors.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = TextStyle(fontSize = 14.sp, color = Color.Gray)
        )
    }
}
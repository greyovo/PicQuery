package me.grey.picquery.ui.widgets

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@InternalTextApi
@Composable
fun SearchInput() {
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    Row {
        TextField(
            value = textValue,
            onValueChange = { textValue = it },
            //            label = { Text("Enter Your Name") },
            placeholder = { Text(text = "John Doe") },
            singleLine = true,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(22.dp)
                ),
            keyboardActions = KeyboardActions(
                onDone = {

                },
                onSearch = {
                    // TODO onSearch
                    Log.d("SEARCH", textValue.text)
                }
            ),
            trailingIcon = {
                IconButton(onClick = { /*TODO*/ }) {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "搜索")
                }
            }

        )

    }

}

@InternalTextApi
@Preview
@Composable
fun SearchInputPreview() {
    SearchInput()
}
package me.grey.picquery.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeTopBar(onClickHelpButton: () -> Unit) {
    TopAppBar(
        actions = {
            IconButton(onClick = {
                onClickHelpButton()
            }) {
                Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null)
            }
        },
        title = {},
    )
}
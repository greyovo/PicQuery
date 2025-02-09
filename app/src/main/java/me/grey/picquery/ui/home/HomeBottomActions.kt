package me.grey.picquery.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.grey.picquery.R

@Composable
fun HomeBottomActions(
    onClickManageAlbum: () -> Unit,
    navigateToSetting: () -> Unit,
    navigateToSimilar: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(bottom = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IndexAlbumButton(onClick = onClickManageAlbum)
        VerticalDivider(
            Modifier
                .height(20.dp)
                .padding(horizontal = 5.dp)
        )
        SimilarPhotoButton(onClick = navigateToSimilar)
        VerticalDivider(
            Modifier
                .height(20.dp)
                .padding(horizontal = 5.dp)
        )
        SettingButton(onClick = navigateToSetting)
    }
}

@Composable
private fun SettingButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Settings, contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun IndexAlbumButton(onClick: () -> Unit) {
    TextButton(
        onClick = { onClick() },
    ) {
        Icon( imageVector = Icons.Default.Photo, contentDescription = "")
        Box(modifier = Modifier.width(5.dp))
        Text(text = stringResource(R.string.index_album_btn))
    }
}

@Composable
private fun SimilarPhotoButton(onClick: () -> Unit) {
    TextButton(
        onClick = { onClick() },
    ) {
        Icon(modifier = Modifier
            .height(24.dp)
            .width(24.dp),
            painter = painterResource(id = R.drawable.ic_similar),
            contentDescription = "")
        Box(modifier = Modifier.width(5.dp))
        Text(text = stringResource(R.string.similar_photos))
    }
}
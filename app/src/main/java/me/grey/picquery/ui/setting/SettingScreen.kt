package me.grey.picquery.ui.setting

import LogoRow
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import me.grey.picquery.R
import me.grey.picquery.common.Constants.PRIVACY_URL
import me.grey.picquery.common.Constants.SOURCE_REPO_URL
import me.grey.picquery.ui.common.BackButton
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(onNavigateBack: () -> Unit, navigateToIndexMgr: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton { onNavigateBack() } }
            )
        }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            item {
                LogoRow(modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp))
            }
            item { InformationRow() }
            item { Box(modifier = Modifier.height(15.dp)) }
            item { UploadLogSettingItem() }
            item { AlbumIndexManagerUIItem(navigateToIndexMgr) }
        }
    }
}

@Composable
private fun UploadLogSettingItem(settingViewModel: SettingViewModel = koinViewModel()) {
    val enable = settingViewModel.enableUploadLog.collectAsState(initial = true)
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Default.PermDeviceInformation,
                contentDescription = "Share Info"
            )
        },
        headlineContent = { Text(text = stringResource(R.string.share_anonymous_data)) },
        supportingContent = { Text(text = stringResource(R.string.share_anonymous_data_statement)) },
        trailingContent = {
            Switch(
                checked = enable.value,
                onCheckedChange = { enabled ->
                    settingViewModel.setEnableUploadLog(enabled)
                }
            )
        },
        modifier = Modifier.clickable { settingViewModel.setEnableUploadLog(!enable.value) }
    )
}

@Composable
private fun InformationRow() {
    val context = LocalContext.current
    fun launchURL(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(context, intent, null)
    }

    Row(
        modifier = Modifier
            .padding(bottom = 15.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { launchURL(PRIVACY_URL) }) {
            Text(text = stringResource(R.string.privacy_policy))
        }
        Divider()
        TextButton(onClick = { launchURL(SOURCE_REPO_URL) }) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = stringResource(R.string.github)
            )
            Box(modifier = Modifier.width(5.dp))
            Text(text = stringResource(R.string.github))
        }
    }
}

@Composable
private fun Divider() {
    VerticalDivider(
        Modifier
            .height(20.dp)
            .padding(horizontal = 3.dp)
    )
}

@Composable
private fun AlbumIndexManagerUIItem(navigateToIndexMgr: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Dataset,
                contentDescription = "Click to Manage Album Indexes"
            )
        },
        headlineContent = { Text(text = stringResource(R.string.album_index_manager_ui_title)) },
        supportingContent = { Text(text = stringResource(R.string.album_index_manager_ui_desc)) },
        modifier = Modifier.clickable { navigateToIndexMgr() }
    )
}

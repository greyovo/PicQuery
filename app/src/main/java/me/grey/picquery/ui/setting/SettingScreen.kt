package me.grey.picquery.ui.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.grey.picquery.R
import me.grey.picquery.ui.common.BackButton
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    settingViewModel: SettingViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { BackButton { onNavigateBack() } },
            )
        }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            item {
                val enable = settingViewModel.enableUploadLog.collectAsState(initial = true)
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.share_anonymous_data)) },
                    supportingContent = { Text(text = stringResource(R.string.share_anonymous_data_statement)) },
                    trailingContent = {
                        Switch(
                            checked = enable.value,
                            onCheckedChange = { enabled ->
                                settingViewModel.setEnableUploadLog(enabled)
                            },
                        )
                    },
                    modifier = Modifier.clickable { settingViewModel.setEnableUploadLog(!enable.value) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(text = "查看日志") },
                    modifier = Modifier.clickable { }
                )
            }
        }
    }
}
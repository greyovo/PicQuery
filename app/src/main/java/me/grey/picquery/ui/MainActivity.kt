package me.grey.picquery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.theme.PicQueryThemeM3
import org.koin.android.ext.android.get
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.annotation.KoinExperimentalAPI

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val preferenceRepo: PreferenceRepository = get()

    @OptIn(KoinExperimentalAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val agreeStateFlow = preferenceRepo.getAgreement()
            .stateIn(
                scope = lifecycleScope,
                started = SharingStarted.Eagerly,
                initialValue = true
            )

        setContent {
            KoinAndroidContext {
                MainContent(agreeStateFlow)
            }
        }
    }

    @Composable
    private fun MainContent(agreeStateFlow: StateFlow<Boolean>) {
        val agreeState by agreeStateFlow.collectAsState(initial = true)

        PicQueryThemeM3 {
            Surface(Modifier.fillMaxSize()) {
                PrivacyAgreementDialog(agreeState)
                if (agreeState) {
                    AppNavHost()
                }
            }
        }
    }

    @Composable
    fun PrivacyAgreementDialog(agreeState: Boolean) {
        if (!agreeState) {
            AlertDialog(
                onDismissRequest = { },
                text = {
                    Text(stringResource(R.string.privacy_statement_dialog))
                },
                title = { Text("欢迎使用图搜") },
                confirmButton = {
                    Button(onClick = {
                        lifecycleScope.launch {
                            preferenceRepo.acceptAgreement()
                        }
                    }) {
                        Text(text = stringResource(id = R.string.privacy_statement_agree))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        lifecycleScope.launch {
                            preferenceRepo.acceptAgreement(enableUploadLog = false)
                        }
                    }) {
                        Text(text = stringResource(id = R.string.privacy_statement_decline))
                    }
                }
            )
        }
    }
}

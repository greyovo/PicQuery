package me.grey.picquery.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.grey.picquery.R
import me.grey.picquery.common.Constants.BUGLY_APP_ID
import me.grey.picquery.common.InitializeEffect
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
        val agreeStateFlow = preferenceRepo.getAgreement()

        super.onCreate(savedInstanceState)
        setContent {
            val agreeState = remember {
                mutableStateOf(runBlocking { agreeStateFlow.first() })
            }
            if (agreeState.value) {
                initBugly()
            }

            InitializeEffect {
                lifecycleScope.launch {
                    agreeStateFlow.collect { agree ->
                        agreeState.value = agree
                    }
                }
            }

            KoinAndroidContext {
                PicQueryThemeM3 {
                    Surface(Modifier.fillMaxSize()) {
                        PrivacyAgreementDialog(agreeState.value)
                        if (agreeState.value) {
                            AppNavHost()
                        }
                    }
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
                    TextButton(onClick = {
                        lifecycleScope.launch {
                            preferenceRepo.acceptAgreement()
                        }
                    }) {
                        Text(text = stringResource(id = R.string.privacy_statement_agree))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { finish() }) {
                        Text(text = stringResource(id = R.string.privacy_statement_decline))
                    }
                },
            )
        }
    }

    /**
     * Bugly, a crash reporter
    * */
    private fun initBugly() {
        lifecycleScope.launch {
            val enable = preferenceRepo.getEnableUploadLog().first()
            if (enable) {
                Log.d(TAG, "Enable CrashReport")
                CrashReport.initCrashReport(applicationContext, BUGLY_APP_ID, false)
            } else {
                Log.d(TAG, "Disable CrashReport")
            }
        }
    }
}

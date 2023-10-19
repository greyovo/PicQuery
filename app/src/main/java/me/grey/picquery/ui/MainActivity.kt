package me.grey.picquery.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.grey.picquery.R
import me.grey.picquery.common.Constants
import me.grey.picquery.common.Constants.BUGLY_APP_ID
import me.grey.picquery.common.InitializeEffect
import me.grey.picquery.data.data_source.PreferenceRepository
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.theme.PicQueryThemeM3
import me.grey.picquery.ui.home.HomeViewModel
import org.koin.android.ext.android.get
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.annotation.KoinExperimentalAPI

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val preferenceRepo: PreferenceRepository = get()
    private val homeViewModel: HomeViewModel = get()
    private val albumManager: AlbumManager = get()

    @OptIn(KoinExperimentalAPI::class, ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val agreeStateFlow = preferenceRepo.getAgreement()
            val agreeState = agreeStateFlow.collectAsState(initial = true)
            val mediaPermissions = rememberMediaPermissions()

            InitializeEffect {
                if (mediaPermissions.allPermissionsGranted) {
                    homeViewModel.doneRequestPermission()
                    albumManager.initAllAlbumList()
                }
            }

            KoinAndroidContext {
                PicQueryThemeM3 {
                    AppNavHost()

                    PrivacyAgreementDialog(agreeState.value)
                }
            }
        }
    }

    @Composable
    fun PrivacyAgreementDialog(agreeState: Boolean) {
        val scope = rememberCoroutineScope()

        if (!agreeState) {
            AlertDialog(
                onDismissRequest = { },
                text = {
                    Text(stringResource(R.string.privacy_statement_dialog))
                },
                title = { Text("欢迎使用图搜") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            preferenceRepo.acceptAgreement()
                        }
                        initBugly()
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
        } else {
            initBugly()
        }
    }

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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberMediaPermissions(
    homeViewModel: HomeViewModel = koinViewModel(),
    albumManager: AlbumManager = koinInject(),
): MultiplePermissionsState {
    val scope = rememberCoroutineScope()
    return rememberMultiplePermissionsState(
        permissions = Constants.PERMISSIONS,
        onPermissionsResult = { permission ->
            if (permission.all { it.value }) {
                scope.launch { albumManager.initAllAlbumList() }
                homeViewModel.doneRequestPermission()
            }
        },
    )
}
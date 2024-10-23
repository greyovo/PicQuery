package me.grey.picquery.common

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

object Constants {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val PERMISSION_T = listOf(Manifest.permission.READ_MEDIA_IMAGES)

    private val PERMISSION_OLD = listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PERMISSION_T
    } else {
        PERMISSION_OLD
    }

    const val DIM = 224

    const val PRIVACY_URL = "https://grey030.gitee.io/pages/picquery/privacy.html"
    const val SOURCE_REPO_URL = "https://github.com/greyovo/PicQuery"

}
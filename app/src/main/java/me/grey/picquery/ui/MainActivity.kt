package me.grey.picquery.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.permissionx.guolindev.PermissionX
import me.grey.picquery.R
import me.grey.picquery.theme.PicQueryThemeM3
import me.grey.picquery.ui.albums.AlbumViewModel
import me.grey.picquery.ui.search.SearchScreen

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val albumViewModel: AlbumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicQueryThemeM3 {
                SearchScreen()
            }
        }
        initAlbum()
    }

    private fun initAlbum() {
        for (p in permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                requestPermission()
                return
            }
        }
        albumViewModel.initAllAlbumList()
    }

    private val permissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        else
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun requestPermission() {
        PermissionX.init(this)
            .permissions(permissions)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    resources.getString(R.string.permission_tips),
                    resources.getString(R.string.ok_button),
                    resources.getString(R.string.cancel_button),
                )
            }.request { allGranted, _, _ ->
                if (!allGranted) {
                    Toast.makeText(this, getString(R.string.no_permission_toast), Toast.LENGTH_LONG)
                        .show()
                } else {
                    albumViewModel.initAllAlbumList()
                }
            }
    }
}


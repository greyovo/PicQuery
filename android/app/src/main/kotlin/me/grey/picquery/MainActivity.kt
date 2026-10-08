package me.grey.picquery

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "picquery/updates")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "architecture" -> result.success(Build.SUPPORTED_ABIS.firstOrNull() ?: "")
                    "install" -> {
                        try {
                            val path = call.argument<String>("path")
                            require(!path.isNullOrBlank()) { "Missing update package" }
                            val file = File(path).canonicalFile
                            val updateDir = File(filesDir, "updates").canonicalFile
                            require(file.parentFile == updateDir && file.extension == "apk" && file.isFile) {
                                "Invalid update package"
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !packageManager.canRequestPackageInstalls()) {
                                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:$packageName")))
                                result.success(false)
                            } else {
                                val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
                                startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                                result.success(true)
                            }
                        } catch (error: Exception) {
                            result.error("install_failed", error.message, null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "picquery/media")
            .setMethodCallHandler { call, result ->
                if (call.method != "openImage") {
                    result.notImplemented()
                    return@setMethodCallHandler
                }
                val path = call.argument<String>("path")
                if (path.isNullOrBlank()) {
                    result.error("invalid_path", "Missing image path", null)
                    return@setMethodCallHandler
                }
                try {
                    // Use the original MediaStore URI so gallery apps can identify
                    // the photo, rather than a FileProvider URI for a detached file.
                    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val uri = contentResolver.query(
                        collection,
                        arrayOf(MediaStore.Images.Media._ID),
                        "${MediaStore.Images.Media.DATA} = ?",
                        arrayOf(path),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) ContentUris.withAppendedId(
                            collection, cursor.getLong(0)
                        ) else null
                    }
                    if (uri == null) {
                        result.success(false)
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, contentResolver.getType(uri) ?: "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                        result.success(true)
                    }
                } catch (error: Exception) {
                    result.error("open_failed", error.message, null)
                }
            }
    }
}

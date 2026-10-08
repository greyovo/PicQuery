package me.grey.picquery

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
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

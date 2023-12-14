package me.grey.picquery.common

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication
import java.io.*

object AssetUtil {

    private const val TAG = "AssetUtil"

    suspend fun copyAssetsFolder(context: Context, sourceAsset: String, targetFolder: File) {
        try {
            withContext(Dispatchers.IO) {
                val assetManager = context.assets
                val assets = assetManager.list(sourceAsset)
                if (!assets.isNullOrEmpty()) {
                    // 创建目标文件夹
                    if (!targetFolder.exists()) {
                        targetFolder.mkdirs()
                    }

                    for (itemInFolder in assets) {
                        val currentAssetPath = "$sourceAsset/$itemInFolder"
                        val isFile = assetManager.list(currentAssetPath)!!.isEmpty()

                        val target = File(targetFolder, itemInFolder)
                        if (isFile) {
                            // The file to copy into
                            copyAssetFile(context, currentAssetPath, target)
                        } else {
                            // The folder to create
                            if (!target.exists()) {
                                target.mkdirs()
                            }
                            // Seek to see if the folder contains any file
                            copyAssetsFolder(context, currentAssetPath, target)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    suspend fun copyAssetFile(context: Context, sourceAsset: String, target: File) {
        if (target.exists() && target.length() > 0) {
            return
        }

        withContext(Dispatchers.IO) {
            val inputStream: InputStream = context.assets.open(sourceAsset)
            val outputStream: OutputStream = FileOutputStream(target)

            inputStream.use { inputs ->
                outputStream.use { os ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (inputs.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                    }
                    os.flush()
                }
            }
        }
    }

    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String): String {
        // 判断本地文件是否存在且有效
        var inputLength: Long
        try {
            val assetStream = PicQueryApplication.context.assets.open(assetName)
            inputLength = assetStream.available().toLong()
            assetStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "assetFilePath: ", e)
            return ""
        }
        val file = File(context.filesDir, assetName)
        val outputLength = file.length()
        if (file.exists() && outputLength > 0) {
            if (outputLength == inputLength) {
                return file.absolutePath
            } else {
                file.writeText("")
            }
        }

        return try {
            runBlocking { copyAssetFile(context, assetName, file) }
            file.absolutePath
        } catch (_: Exception) {
            ""
        }
    }
}
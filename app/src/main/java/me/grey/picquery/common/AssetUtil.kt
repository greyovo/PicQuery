package me.grey.picquery.common

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

object AssetUtil {

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
    fun copyAssetFile(context: Context, sourceAsset: String, target: File) {
        if (target.exists() && target.length() > 0) {
            return
        }

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
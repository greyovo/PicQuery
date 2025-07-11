package me.grey.picquery.common

import android.content.Context
import java.io.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

object AssetUtil {

    private const val TAG = "AssetUtil"

    suspend fun copyAssetsFolder(
        context: Context,
        sourceAsset: String,
        targetFolder: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        withContext(dispatcher) {
            tryCatch {
                launch {
                    val assetManager = context.assets
                    val assets = assetManager.list(sourceAsset)
                    if (!assets.isNullOrEmpty()) {
                        createTargetFolder(targetFolder)
                        copyAssets(context, sourceAsset, targetFolder, assets)
                    }
                }
            }.fold(
                left = { Timber.tag(TAG).e(it, "copyAssetsFolder: ") },
                right = { Timber.tag(TAG).d("copyAssetsFolder: Success") }
            )
        }
    }

    private fun createTargetFolder(targetFolder: File) {
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }
    }

    private suspend fun copyAssets(
        context: Context,
        sourceAsset: String,
        targetFolder: File,
        assets: Array<String>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        val assetManager = context.assets
        for (itemInFolder in assets) {
            val currentAssetPath = "$sourceAsset/$itemInFolder"
            val isFile = assetManager.list(currentAssetPath)!!.isEmpty()

            val target = File(targetFolder, itemInFolder)
            if (isFile) {
                withContext(dispatcher) {
                    copyAssetFile(context, currentAssetPath, target)
                }
            } else {
                createTargetFolder(target)
                copyAssetsFolder(context, currentAssetPath, target)
            }
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

    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String): String {
        val inputLength: Long
        try {
            val assetStream = context.assets.open(assetName)
            inputLength = assetStream.available().toLong()
            assetStream.close()
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "assetFilePath: ")
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
            copyAssetFile(context, assetName, file)
            file.absolutePath
        } catch (_: Exception) {
            ""
        }
    }

    @Throws(IOException::class)
    fun assetFile(context: Context, assetName: String): File? {
        val inputLength: Long
        try {
            val assetStream = context.assets.open(assetName)
            inputLength = assetStream.available().toLong()
            assetStream.close()
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "assetFilePath: ")
            return null
        }
        val file = File(context.filesDir, assetName)
        val outputLength = file.length()
        if (file.exists() && outputLength > 0) {
            if (outputLength == inputLength) {
                return file
            } else {
                file.writeText("")
            }
        }

        return try {
            copyAssetFile(context, assetName, file)
            file
        } catch (_: Exception) {
            null
        }
    }
}

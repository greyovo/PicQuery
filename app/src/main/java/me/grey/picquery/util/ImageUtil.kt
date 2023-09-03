package me.grey.picquery.util

import android.content.Context
import android.graphics.*
import android.util.Log
import android.util.Size
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.grey.picquery.core.encoder.ImageEncoder
import java.io.*

@Throws(IOException::class)
fun assetFilePath(context: Context, assetName: String): String {
    val file = File(context.filesDir, assetName)
    if (file.exists() && file.length() > 0) {
        return file.absolutePath
    }
    try {
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    } catch (_: Exception) {
        return ""
    }
}

/**
 * https://developer.android.google.cn/topic/performance/graphics/load-bitmap?hl=zh-cn
 * */
fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {

        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

/**
 * https://developer.android.google.cn/topic/performance/graphics/load-bitmap?hl=zh-cn
 * */
fun decodeSampledBitmapFromFileDescriptor(
    fd: FileDescriptor,
    size: Size,
): Bitmap {
    return BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFileDescriptor(fd, null, this)

        // Calculate inSampleSize
        inSampleSize = calculateInSampleSize(this, size.width, size.height)

        // Decode bitmap with inSampleSize set
        inJustDecodeBounds = false

        BitmapFactory.decodeFileDescriptor(fd, null, this)!!
    }
}

fun decodeSampledBitmapFromFile(
    pathName: String,
    size: Size,
): Bitmap {
    // First decode with inJustDecodeBounds=true to check dimensions
    return BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(pathName, this)

        // Calculate inSampleSize
        inSampleSize = calculateInSampleSize(this, size.width, size.height)

        // Decode bitmap with inSampleSize set
        inJustDecodeBounds = false

        BitmapFactory.decodeFile(pathName, this)
    }

}

fun saveBitMap(context: Context, bitmap: Bitmap, name: String) {
    try {
        val file = File(context.filesDir.path + "/$name.png")
        if (!file.exists()) {
            file.createNewFile()
        }
        val out = FileOutputStream(file)

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)

        // 刷新输出流并关闭
        out.flush()
        out.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun loadThumbnail(context: Context, imagePath: String): Bitmap {
    fun useGlide(): Boolean {
        val file = File(imagePath)
        return if (file.exists()) {
            file.length() > 3000000 // > 3MB
        } else {
            false
        }
    }

    if (useGlide()) {
        Log.d("loadThumbnail", "using Glide")
        return withContext(Dispatchers.IO) {
            Glide.with(context)
                .asBitmap()
                .load(imagePath)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .downsample(DownsampleStrategy.FIT_CENTER)
                .override(224)
                .skipMemoryCache(true)
                .submit().get()
        }
    }
    Log.d("loadThumbnail", "using BitmapFactory")
    return decodeSampledBitmapFromFile(imagePath, ImageEncoder.INPUT_SIZE)
}
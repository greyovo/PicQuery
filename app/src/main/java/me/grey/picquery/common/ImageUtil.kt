package me.grey.picquery.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.Size
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import me.grey.picquery.common.Constants.DIM
import me.grey.picquery.data.model.Photo

private const val TAG = "ImageUtil"

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

fun decodeSampledBitmapFromFile(
    pathName: String,
    size: Size,
): Bitmap? {
    // First decode with inJustDecodeBounds=true to check dimensions
    return try {
        BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(pathName, this)
            inSampleSize = calculateInSampleSize(this, size.width, size.height)
            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false
            BitmapFactory.decodeFile(pathName, this)
        }
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Failed to decode file: $pathName, ${e.message}")
        null
    }
}

val IMAGE_INPUT_SIZE = Size(DIM, DIM)

suspend fun loadThumbnail(context: Context, photo: Photo, size: Size = IMAGE_INPUT_SIZE): Bitmap? {
    return flow<Bitmap?> {
        emit(context.contentResolver.loadThumbnail(photo.uri, size, null))
    }.catch {
        emit(
            coroutineScope {
                Glide.with(context)
                    .asBitmap()
                    .load(photo.path)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .downsample(DownsampleStrategy.FIT_CENTER)
                    .override(DIM)
                    .skipMemoryCache(true)
                    .submit().get()
            }
        )
    }.catch {
        emit(decodeSampledBitmapFromFile(photo.path, size))
    }.first()
}

fun preprocess(bitmap: Bitmap): Bitmap {
    // bitmap size to 224x224
    return Bitmap.createScaledBitmap(bitmap, DIM, DIM, true)
}

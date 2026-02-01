package me.grey.picquery.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Size
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.grey.picquery.common.Constants.DIM
import me.grey.picquery.data.model.Photo
import timber.log.Timber
import androidx.core.graphics.scale

private const val TAG = "ImageUtil"

/**
 * 图片缓存管理器 - 带内存缓存
 */
object ThumbnailCache {
    // 内存缓存：使用 1/8 的可用内存
    internal val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }
    
    internal fun generateCacheKey(path: String, size: Size): String {
        return "${path}_${size.width}x${size.height}"
    }
}

/**
 * Extension function for BitmapFactory.Options to calculate optimal inSampleSize
 * https://developer.android.google.cn/topic/performance/graphics/load-bitmap?hl=zh-cn
 */
fun BitmapFactory.Options.calculateInSampleSize(reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val (height, width) = outHeight to outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

fun decodeSampledBitmapFromFile(pathName: String, size: Size): Bitmap? {
    // First decode with inJustDecodeBounds=true to check dimensions
    return try {
        BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(pathName, this)
            // Use extension function
            inSampleSize = calculateInSampleSize(size.width, size.height)
            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false
            BitmapFactory.decodeFile(pathName, this)
        }
    } catch (e: IllegalArgumentException) {
        Timber.tag(TAG).w("Failed to decode file: $pathName, ${e.message}")
        null
    }
}

val IMAGE_INPUT_SIZE = Size(DIM, DIM)

/**
 * 加载缩略图 - 优化版本
 * 1. 先检查内存缓存
 * 2. 尝试 ContentResolver
 * 3. 使用 Coil (原生协程支持)
 * 4. 最后使用 BitmapFactory
 */
suspend fun loadThumbnail(context: Context, photo: Photo, size: Size = IMAGE_INPUT_SIZE): Bitmap? {
    // 1. 检查内存缓存
    val cacheKey = ThumbnailCache.generateCacheKey(photo.path, size)
    ThumbnailCache.memoryCache[cacheKey]?.let { 
        Timber.tag(TAG).v("Cache hit for ${photo.path}")
        return it 
    }
    
    // 2. 尝试 ContentResolver
    try {
        val thumbnail = context.contentResolver.loadThumbnail(photo.uri, size, null)
        ThumbnailCache.memoryCache.put(cacheKey, thumbnail)
        return thumbnail
    } catch (e: Exception) {
        Timber.tag(TAG).v("ContentResolver failed: ${e.message}")
    }
    
    // 3. 使用 Coil (原生协程支持，非阻塞)
    try {
        val bitmap = loadImageWithCoil(context, photo.path, size)
        bitmap?.let { ThumbnailCache.memoryCache.put(cacheKey, it) }
        return bitmap
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Coil load failed")
    }
    
    // 4. 最后的 fallback
    return withContext(Dispatchers.IO) {
        decodeSampledBitmapFromFile(photo.path, size)?.also {
            ThumbnailCache.memoryCache.put(cacheKey, it)
        }
    }
}

/**
 * 使用 Coil 加载图片 - 原生协程支持
 * Coil 是 Kotlin-first 的图片加载库，内置协程支持
 */
private suspend fun loadImageWithCoil(context: Context, path: String, size: Size): Bitmap? {
    val imageLoader = ImageLoader.Builder(context)
        .crossfade(false)
        .build()
    
    val request = ImageRequest.Builder(context)
        .data(path)
        .size(size.width, size.height)
        .scale(Scale.FIT)
        .allowHardware(false)  // 需要 Bitmap 进行后续处理
        .build()
    
    return when (val result = imageLoader.execute(request)) {
        is SuccessResult -> {
            val drawable = result.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap
            } else {
                null
            }
        }
        else -> null
    }
}

fun preprocess(bitmap: Bitmap): Bitmap {
    // bitmap size to 224x224
    return bitmap.scale(DIM, DIM)
}

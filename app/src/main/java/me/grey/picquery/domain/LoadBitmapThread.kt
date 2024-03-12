package me.grey.picquery.domain

import android.util.Log
import kotlinx.coroutines.runBlocking
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.common.loadThumbnail
import me.grey.picquery.data.model.Photo
import me.grey.picquery.data.model.PhotoBitmap
import me.grey.picquery.domain.encoder.ImageEncoder

class LoadBitmapThread(
    private val queue: PreloadPhotosQueue,
    private val photos: List<Photo>,
    private val imageEncoder: ImageEncoder
) : Thread() {
    private companion object {
        const val TAG = "LoadBitmapThread"
    }

    override fun run() {
        runBlocking {
            for (photo in photos) {
                // load bitmap
                val thumbnailBitmap = loadThumbnail(PicQueryApplication.context, photo)
                if (thumbnailBitmap == null) {
                    Log.w(TAG, "Unsupported file: '${photo.path}', skip encoding it.")
                    continue
                }
                // preprocess
                val prepBitmap = imageEncoder.preprocess(thumbnailBitmap)
                queue.put(PhotoBitmap(photo, prepBitmap))
            }
            queue.markPreloadComplete()
        }
    }
}
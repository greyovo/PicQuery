package me.grey.picquery.data.data_source

import android.content.ContentResolver
import android.content.ContentResolver.*
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import me.grey.picquery.data.CursorUtil
import me.grey.picquery.data.model.Photo
import java.io.InputStream


class PhotoRepository(private val contentResolver: ContentResolver) {

    companion object {
        private const val TAG = "PhotoRepository"
    }

    var callback: IAlbumQuery? = null

    fun photoFlow() = callbackFlow {
        callback = IAlbumQuery { photos -> trySend(photos) }

        awaitClose { callback = null }
    }

    private val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE, // in Bytes
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )

    private val imageCollection: Uri =
        MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL
        )

    fun getPhotoListByAlbumId(albumId: Long): List<Photo> {
        val query = contentResolver.query(
            imageCollection,
            imageProjection,
            Bundle().apply {
                putInt(
                    QUERY_ARG_SORT_DIRECTION,
                    QUERY_SORT_DIRECTION_DESCENDING
                )
                putStringArray(
                    QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED)
                )
                putString(
                    QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Images.Media.BUCKET_ID}=?"
                )
                putStringArray(QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(albumId.toString()))
            },
            null
        )
        val photoList = mutableListOf<Photo>()
        query?.use { cursor: Cursor ->
            while (cursor.moveToNext()) {
                photoList.add(CursorUtil.getPhoto(cursor))
            }
        }
        return photoList
    }

    fun getPhotoListByPage(albumId: Long, pageIndex: Int = 0, pageSize: Int = 1000): List<Photo> {
        val offset = pageIndex * pageSize
        val query = contentResolver.query(
            imageCollection,
            imageProjection,
            Bundle().apply {
                putInt(
                    QUERY_ARG_SORT_DIRECTION,
                    QUERY_SORT_DIRECTION_DESCENDING
                )
                putStringArray(
                    QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED)
                )
                putString(
                    QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Images.Media.BUCKET_ID}=?"
                )
                putStringArray(QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(albumId.toString()))
                putInt(QUERY_ARG_OFFSET, offset)
                putInt(QUERY_ARG_LIMIT, pageSize)
            },
            null
        )
        val photoList = mutableListOf<Photo>()
        query?.use { cursor: Cursor ->
            while (cursor.moveToNext()) {
                photoList.add(CursorUtil.getPhoto(cursor))
            }
        }
        callback?.onAlbumQuery(photoList)
        return photoList
    }

    fun getPhotoById(id: Long): Photo? {
        val queryPhotoById = contentResolver.query(
            imageCollection,
            imageProjection,
            "${MediaStore.Images.Media._ID} = ?",
            arrayOf(id.toString()),
            null,
        )
        return queryPhotoById.use { cursor: Cursor? ->
            cursor?.moveToFirst()
            if (cursor != null) {
                CursorUtil.getPhoto(cursor)
            } else {
                null
            }
        }
    }

    suspend fun getAllPhotos(albumId: Long): Int {
        var page = 0
        val pageSize = 50 // 每页的大小
        var size = 0
        while (true) {
            val photos = getPhotoListByPage(albumId, pageIndex = page, pageSize = pageSize)
            if (photos.isEmpty()) break
            size += photos.size
            page++
        }
        return size
    }

    fun getPhotoListByIds(ids: List<Long>): List<Photo> {
        val query = contentResolver.query(
            imageCollection,
            imageProjection,
            "${MediaStore.Images.Media._ID} IN (${ids.joinToString(",")})",
            arrayOf(),
            null,
        )
        val result = query.use { cursor: Cursor? ->
            when (cursor?.count) {
                null -> {
                    Log.e(TAG, "getPhotoListByIds, cursor null")
                    emptyList()
                }
                0 -> {
                    Log.w(TAG, "getPhotoListByIds, need ${ids.size} but found 0!")
                    emptyList()
                }
                else -> {
                    // 开始从结果中迭代查找，cursor最初从-1开始
                    val photoList = mutableListOf<Photo>()
                    while (cursor.moveToNext()) {
                        photoList.add(CursorUtil.getPhoto(cursor))
                    }
                    photoList
                }
            }
        }
        return result
    }


    fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            // 打开输入流
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            // 解码输入流为 Bitmap
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}
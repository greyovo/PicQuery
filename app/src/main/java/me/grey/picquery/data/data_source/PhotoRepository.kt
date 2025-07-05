package me.grey.picquery.data.data_source

import android.content.ContentResolver.*
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import java.io.InputStream
import kotlinx.coroutines.flow.flow
import me.grey.picquery.data.CursorUtil
import me.grey.picquery.data.model.Photo

class PhotoRepository(private val context: Context) {

    companion object {
        private const val TAG = "PhotoRepository"
        private const val DEFAULT_PAGE_SIZE = 500
    }

    private val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE, // in Bytes
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )

    private val imageCollection: Uri =
        MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL
        )

    private fun getPhotoListByAlbumIdFlow(albumId: Long, pageSize: Int = DEFAULT_PAGE_SIZE) = flow {
        var pageIndex = 0
        while (true) {
            val photos = getPhotoListByPage(albumId, pageIndex, pageSize)
            if (photos.isEmpty()) {
                break
            }
            emit(photos)
            pageIndex++
        }
    }

    suspend fun getPhotoListByAlbumId(albumId: Long): List<Photo> {
        val result = mutableListOf<Photo>()
        getPhotoListByAlbumIdFlow(albumId).collect {
            result.addAll(it)
        }

        return result
    }

    private fun getPhotoListByPage(albumId: Long, pageIndex: Int = 0, pageSize: Int = 1000): List<Photo> {
        val offset = pageIndex * pageSize
        val query = context.contentResolver.query(
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
        return photoList
    }

    fun getPhotoById(id: Long): Photo? {
        val queryPhotoById = context.contentResolver.query(
            imageCollection,
            imageProjection,
            "${MediaStore.Images.Media._ID} = ?",
            arrayOf(id.toString()),
            null
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

    fun getImageCountInAlbum(albumId: Long): Int {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())

        val cursor = context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            null
        )

        return cursor?.use {
            it.count
        } ?: 0
    }

    fun getPhotoListByIds(ids: List<Long>): List<Photo> {
        val query = context.contentResolver.query(
            imageCollection,
            imageProjection,
            "${MediaStore.Images.Media._ID} IN (${ids.joinToString(",")})",
            arrayOf(),
            null
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
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            // 解码输入流为 Bitmap
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 分批获取相册中的照片，以 Flow 形式返回
     * @param albumId 相册ID
     * @param pageSize 每批照片的数量
     * @return Flow<List<Photo>> 照片列表流
     */
    fun getPhotoListByAlbumIdPaginated(albumId: Long, pageSize: Int = DEFAULT_PAGE_SIZE) = flow {
        var pageIndex = 0
        while (true) {
            val photos = getPhotoListByPage(albumId, pageIndex, pageSize)
            if (photos.isEmpty()) {
                break
            }
            emit(photos)
            pageIndex++
        }
    }
}

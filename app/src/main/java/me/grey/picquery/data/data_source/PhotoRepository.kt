package me.grey.picquery.data.data_source

//import android.content.ContentResolver
import android.content.ContentResolver
import android.content.ContentResolver.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import me.grey.picquery.data.CursorUtil
import me.grey.picquery.data.model.Photo
import java.util.Arrays


class PhotoRepository(private val contentResolver: ContentResolver) {

    companion object {
        private const val TAG = "PhotoRepository"
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

    fun getPhotoList(pageIndex: Int = 0, pageSize: Int = 50): List<Photo> {
        val offset = pageIndex * pageSize

        val queryAllPhoto = contentResolver.query(
            imageCollection,
            imageProjection,
            Bundle().apply {
                putInt(QUERY_ARG_OFFSET, offset)
                putInt(QUERY_ARG_LIMIT, pageSize)
            },
            null,
        )
        val photoList = mutableListOf<Photo>()

        queryAllPhoto.use { cursor: Cursor? ->
            when (cursor?.count) {
                null -> {
                    Log.e(TAG, "getPhotoList, cursor null")
                    return emptyList()
                }

                0 -> return emptyList()
                else -> {
                    // 开始从结果中迭代查找，cursor最初从-1开始
                    cursor.move(offset)
                    while (cursor.moveToNext()) {
                        if (photoList.size >= pageSize) break
                        photoList.add(CursorUtil.getPhoto(cursor))
                    }
                    return photoList
                }
            }
        }
    }


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
                putStringArray(QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(albumId.toString()));
            },
            null
        )

        query.use { cursor: Cursor? ->
            when (cursor?.count) {
                null -> {
                    Log.e(TAG, "getPhotoListByAlbumId, cursor null")
                    return emptyList()
                }

                0 -> return emptyList()
                else -> {
                    // 开始从结果中迭代查找，cursor最初从-1开始
                    val photoList = mutableListOf<Photo>()
                    while (cursor.moveToNext()) {
                        photoList.add(CursorUtil.getPhoto(cursor))
                    }
                    return photoList
                }
            }
        }
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

    fun getPhotoListByIds(ids: List<Long>): List<Photo> {
        val query = contentResolver.query(
            imageCollection,
            imageProjection,
            "${MediaStore.Images.Media._ID} IN (${ids.joinToString(",")})",
            arrayOf(),
            null,
        )
        query.use { cursor: Cursor? ->
            when (cursor?.count) {
                null -> {
                    Log.e(TAG, "getPhotoListByIds, cursor null")
                    return emptyList()
                }

                0 -> {
                    Log.w(TAG, "getPhotoListByIds, need ${ids.size} but found 0!")
                    return emptyList()
                }

                else -> {
                    // 开始从结果中迭代查找，cursor最初从-1开始
                    val photoList = mutableListOf<Photo>()
                    while (cursor.moveToNext()) {
                        photoList.add(CursorUtil.getPhoto(cursor))
                    }
                    return photoList
                }
            }
        }
    }


}
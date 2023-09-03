package me.grey.picquery.data

//import android.content.ContentResolver
import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import me.grey.picquery.data.model.Photo


class PhotoRepository(private val contentResolver: ContentResolver) {

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE, // in Bytes
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
    )

    private val collection: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
            )
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private val queryAllPhoto = contentResolver.query(
        collection,
        projection,
        null,
        null,
        null,
    )

    fun getPhotoList(pageIndex: Int = 0, pageSize: Int = 50): List<Photo> {
        val photoList = mutableListOf<Photo>()
        val offset = pageIndex * pageSize

        queryAllPhoto.use { cursor: Cursor? ->
            // 开始从结果中迭代查找
            cursor!!.move(offset)
            while (cursor.moveToNext()) {
                if (photoList.size >= pageSize) break
                photoList.add(getPhotoFromCursor(cursor))
            }
            return photoList
        }
    }

    fun getPhotoById(id: Long): Photo? {
        val queryPhotoById = contentResolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media._ID} = ?",
            arrayOf(id.toString()),
            null,
        )
        return queryPhotoById.use { cursor: Cursor? ->
            if (cursor != null) {
                getPhotoFromCursor(cursor)
            } else {
                null
            }
        }
    }

    private fun getPhotoFromCursor(cursor: Cursor): Photo {
        var albumIDColumn: Int? = null
        var albumLabelColumn: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            albumIDColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            albumLabelColumn =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        }

        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        val label =
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
        val timestamp =
            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED))
        val albumID = albumIDColumn?.let { cursor.getLong(it) }
        val albumLabel = albumLabelColumn?.let { cursor.getString(it) }
        val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
        val contentUri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id
        )

        return Photo(
            id = id,
            uri = contentUri,
            label = label,
            albumID = albumID,
            albumLabel = albumLabel,
            timestamp = timestamp,
            path = path
        )
    }
}
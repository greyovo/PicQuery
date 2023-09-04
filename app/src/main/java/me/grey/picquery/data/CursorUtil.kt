package me.grey.picquery.data

import android.content.ContentUris
import android.database.Cursor
import android.provider.MediaStore
import me.grey.picquery.data.model.Photo

class CursorUtil {

    companion object {
        fun getPhoto(cursor: Cursor): Photo {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val label =
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
            val timestamp =
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED))
            val albumID =
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
            val albumLabel =
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME))
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
}
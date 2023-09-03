package me.grey.picquery.data

//import android.content.ContentResolver
import android.content.ContentResolver
import android.content.ContentResolver.QUERY_ARG_LIMIT
import android.content.ContentResolver.QUERY_ARG_OFFSET
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import me.grey.picquery.data.model.Photo


class PhotoRepository(private val contentResolver: ContentResolver) {

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DISPLAY_NAME,
//        MediaStore.Images.Media.SIZE,
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

    // Show only videos that are at least 5 minutes in duration.
//    private val selection = "${MediaStore.Video.Media.DURATION} >= ?"
//    private val selectionArgs = arrayOf(
//        TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES).toString()
//    )

    //    @RequiresApi(Build.VERSION_CODES.Q)
    fun getPhotos(pageIndex: Int = 0, pageSize: Int = 50): List<Photo> {
        val photoList = mutableListOf<Photo>()
        // 添加 LIMIT 和 OFFSET 子句
        val offset = pageIndex * pageSize
//        val limitClause = "LIMIT $pageSize OFFSET $offset"
        val query = contentResolver.query(
            collection,
            projection,
            null,
            null,
            null,
        )
        query.use { cursor: Cursor? ->
            // Cache column indices.
            val idColumn = cursor!!.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
//            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val timestampColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val albumIDColumn: Int? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
            val albumLabelColumn: Int? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
//            cursor.moveToFirst()
//            cursor.move(offset)
            while (cursor.moveToNext()) {
                if (photoList.size >= pageSize)
                    break

                val id = cursor.getLong(idColumn)
                val label = cursor.getString(nameColumn)
                val timestamp = cursor.getLong(timestampColumn)
                val albumID = if (albumIDColumn != null) {
                    cursor.getLong(albumIDColumn)
                } else {
                    null
                }
                val albumLabel = if (albumLabelColumn != null) {
                    cursor.getString(albumLabelColumn)
                } else {
                    null
                }
//                val path = cursor.getString()
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // val thumbnailBitmap = contentResolver.loadThumbnail(contentUri, Size(224, 224), null)
                photoList.add(
                    Photo(
                        id = id,
                        uri = contentUri,
                        label = label,
                        albumID = albumID,
                        albumLabel = albumLabel,
                        timestamp = timestamp,
                    )
                )
            }
            return photoList
        }
    }
}
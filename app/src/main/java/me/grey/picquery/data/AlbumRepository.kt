package me.grey.picquery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import me.grey.picquery.data.model.Album


class AlbumRepository(private val contentResolver: ContentResolver) {

    companion object {
        private const val TAG = "AlbumRepository"
    }

    private val albumProjection = arrayOf(
        MediaStore.MediaColumns.BUCKET_ID,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.DATE_TAKEN
    )

    private val albumCollection: Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)


    // 查询相册
    // 本质上其实是还是遍历所有的图片，但是手动将他们归类和统计数量
    fun getAlbums(): List<Album> {
        val queryAlbums = contentResolver.query(
            albumCollection,
            albumProjection,
            null,
            null,
        )
        val albumList = mutableListOf<Album>()
        queryAlbums.use { cursor: Cursor? ->
            when (cursor?.count) {
                null -> {
                    Log.e(TAG, "getAlbums, queryAlbums, cursor null")
                    return emptyList()
                }
                0 -> return emptyList()
                else -> {
                    // cursor最初从-1开始
                    while (cursor.moveToNext()) {
                        val photo = CursorUtil.getPhoto(cursor)
                        val i = albumList.indexOfFirst { it.id == photo.albumID }
                        if (i == -1) {
                            albumList.add(
                                Album(
                                    id = photo.albumID,
                                    label = photo.albumLabel,
                                    coverPath = photo.path,
                                    timestamp = photo.timestamp,
                                    count = 1,
                                )
                            )
                        } else {
                            albumList[i].count++
                            if (albumList[i].timestamp <= photo.timestamp) {
                                albumList[i].timestamp = photo.timestamp
                                albumList[i].coverPath = photo.path
                            }
                        }
                    }
                    return albumList
                }
            }
        }
    }
}
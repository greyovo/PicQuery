@file:OptIn(ExperimentalPermissionsApi::class)

package me.grey.picquery.domain

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.R
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import me.grey.picquery.ui.albums.IndexingAlbumState

class AlbumManager(
    private val albumRepository: AlbumRepository,
    private val photoRepository: PhotoRepository,
    private val imageSearcher: ImageSearcher,
) {
    companion object {
        private const val TAG = "AlbumViewModel"
    }

    val indexingAlbumState = mutableStateOf(IndexingAlbumState())

    val isEncoderBusy: Boolean
        get() = indexingAlbumState.value.isBusy

    private val albumList = mutableStateListOf<Album>()
    val searchableAlbumList = mutableStateListOf<Album>()
    val unsearchableAlbumList = mutableStateListOf<Album>()
    val albumsToEncode = mutableStateListOf<Album>()

    val photoFlow = photoRepository.photoFlow()

    private fun searchableAlbumFlow() = albumRepository.getSearchableAlbumFlow()

    private var initialized = false

    suspend fun initAllAlbumList() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            // 本机中的相册
            val albums = albumRepository.getAllAlbums()
            albumList.addAll(albums)
            Log.d(TAG, "ALL albums: ${albums.size}")
            this@AlbumManager.initialized = true
            initDataFlow()
        }
    }

    private suspend fun initDataFlow() {
        searchableAlbumFlow().collect {
            // 从数据库中检索已经索引的相册
            // 有些相册可能已经索引但已被删除，因此要从全部相册中筛选，而不能直接返回数据库的结果
            searchableAlbumList.clear()
            searchableAlbumList.addAll(it)
            searchableAlbumList.sortByDescending { album: Album -> album.count }
            // 从全部相册减去已经索引的ID，就是未索引的相册
            val unsearchable = albumList.filter { all -> !it.contains(all) }
            unsearchableAlbumList.clear()
            unsearchableAlbumList.addAll(unsearchable)
            unsearchableAlbumList.sortByDescending { album: Album -> album.count }
        }
    }

    fun toggleAlbumSelection(album: Album) {
        if (albumsToEncode.contains(album)) {
            albumsToEncode.remove(album)
        } else {
            albumsToEncode.add(album)
        }
    }

    fun toggleSelectAllAlbums() {
        if (albumsToEncode.size != unsearchableAlbumList.size) {
            albumsToEncode.clear()
            albumsToEncode.addAll(unsearchableAlbumList)
        } else {
            albumsToEncode.clear()
        }
    }


    fun encodeAlbums(albums: List<Album>) {

        PicQueryApplication.applicationScope.launch{
            if (albums.isEmpty()) {
                showToast(context.getString(R.string.no_album_selected))
                return@launch
            }

            indexingAlbumState.value =
                IndexingAlbumState(status = IndexingAlbumState.Status.Loading)

            val photos = mutableListOf<Photo>()
            var totalSize = 0
            albums.forEach {
                photos.addAll(photoRepository.getPhotoListByAlbumId(it.id))
//                totalSize += photoRepository.getAllPhotos(it.id)
            }

            val success =
                imageSearcher.encodePhotoListV2(photos) { cur, total, cost ->
                    indexingAlbumState.value = indexingAlbumState.value.copy(
                        current = cur,
                        total = total,
                        cost = cost,
                        status = IndexingAlbumState.Status.Indexing
                    )
                }

            if (success) {
                // 等待完全Encode完毕之后，再向数据库添加一条记录，表示该album已被索引
                Log.i(TAG, "encode ${albums.size} album(s) finished!")
                withContext(Dispatchers.IO) {
                    albumRepository.addAllSearchableAlbum(albums)
                }
                indexingAlbumState.value = indexingAlbumState.value.copy(
                    status = IndexingAlbumState.Status.Finish
                )
            } else {
                Log.w(TAG, "encodePhotoList failed! Maybe too much request.")
            }
        }
    }

    fun clearIndexingState() {
        indexingAlbumState.value = IndexingAlbumState()
    }
}
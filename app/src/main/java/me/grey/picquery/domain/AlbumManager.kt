@file:OptIn(ExperimentalPermissionsApi::class)

package me.grey.picquery.domain

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.R
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import me.grey.picquery.ui.albums.IndexingAlbumState
import org.koin.java.KoinJavaComponent.inject

class AlbumManager(
    private val albumRepository: AlbumRepository,
    private val photoRepository : PhotoRepository
) {
    companion object {
        private const val TAG = "AlbumViewModel"
    }

    val indexingAlbumState = mutableStateOf(IndexingAlbumState())
    val isBottomSheetOpen = mutableStateOf(false)

    val albumList = mutableStateListOf<Album>()
    val searchableAlbumList = mutableStateListOf<Album>()
    val unsearchableAlbumList = mutableStateListOf<Album>()
    val albumsToEncode = mutableStateListOf<Album>()

    private fun searchableAlbumFlow() = albumRepository.getSearchableAlbumFlow()

    private var initialized = false

    init {
        Log.d(TAG, "init $TAG")
    }

    suspend fun initAllAlbumList() {
        Log.d(TAG, this.hashCode().toString())
        if (initialized) return
        withContext(Dispatchers.IO) {
            // 本机中的相册
            val albums = albumRepository.getAllAlbums()
            albumList.addAll(albums)
            Log.d(TAG, "ALL albums: ${albums.size}")
//            // 从数据库中检索已经索引的相册
//            // 有些相册可能已经索引但已被删除，因此要从全部相册中筛选，而不能直接返回数据库的结果
//            val searchable =
//                albumRepository.getSearchableAlbums().filter { albums.contains(it) }
//            searchableAlbumList.addAll(searchable)
//            // 从全部相册减去已经索引的ID，就是未索引的相册
//            val unsearchable = albums.filter { !searchable.contains(it) }
//            unsearchableAlbumList.addAll(unsearchable)
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

    suspend fun encodeSelectedAlbums() {
        if (albumsToEncode.isNotEmpty()) {
            encodeAlbums(albumsToEncode.toList())
            albumsToEncode.clear()
        }
    }

    val imageSearcher: ImageSearcher by inject(ImageSearcher::class.java)

    private suspend fun encodeAlbums(albums: List<Album>) {
        indexingAlbumState.value =
            IndexingAlbumState(status = IndexingAlbumState.Status.Loading)
        withContext(Dispatchers.Default) {
            val photos = mutableListOf<Photo>()
            albums.forEach {
                photos.addAll(photoRepository.getPhotoListByAlbumId(it.id))
            }
            Log.d(TAG, photos.size.toString())
            val success = runBlocking {
                imageSearcher.encodePhotoList(photos) { cur, total, cost ->
                    indexingAlbumState.value = indexingAlbumState.value.copy(
                        current = cur,
                        total = total,
                        cost = cost,
                        status = IndexingAlbumState.Status.Indexing
                    )
                }
            }
            if (success) {
                // 等待完全Encode完毕之后，再向数据库添加一条记录，表示该album已被索引
                Log.i(TAG, "encode ${albums.size} album(s) finished!")

                albumRepository.addAllSearchableAlbum(albums)
                indexingAlbumState.value = indexingAlbumState.value.copy(
                    status = IndexingAlbumState.Status.Finish
                )
            } else {
                Log.w(TAG, "encodePhotoList failed! Maybe too much request.")
            }
        }
    }

    fun openBottomSheet() {
        if (indexingAlbumState.value.isBusy) {
            showToast(PicQueryApplication.context.getString(R.string.busy_when_add_album_toast))
            return
        }
        isBottomSheetOpen.value = true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    suspend fun closeBottomSheet(sheetState: SheetState) {
        sheetState.hide()
        isBottomSheetOpen.value = false
    }

    fun clearIndexingState() {
        indexingAlbumState.value = IndexingAlbumState()
    }
}
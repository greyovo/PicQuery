package me.grey.picquery.ui.main

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.common.showToast
import me.grey.picquery.core.ImageSearcher
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.ui.DevActivity
import me.grey.picquery.ui.result.SearchResultActivity

data class SearchScreenState(
    val albumList: List<Album>,
    val searchableAlbumList: List<Album>,
    val unsearchableAlbumList: List<Album>,
    var currentId: Long,
    var currentProgress: Float
)

class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

//    val albumList: LiveData<List<Album>>
//        get() = _albumList
//
//    val searchableAlbumList: LiveData<List<Album>>
//        get() = _searchableAlbumList
//    val unsearchableAlbumList: LiveData<List<Album>>
//        get() = _unsearchableAlbumList

    val albumList = mutableStateListOf<Album>()
    val searchableAlbumList = mutableStateListOf<Album>()
    val unsearchableAlbumList = mutableStateListOf<Album>()


    private val albumRepository = AlbumRepository(PicQueryApplication.context.contentResolver)
    private val photoRepository = PhotoRepository(PicQueryApplication.context.contentResolver)

    fun searchableAlbumFlow() = albumRepository.getSearchableAlbumFlow()

    fun initAll() {
        initAllAlbumList()
    }

    private suspend fun initDataFlow() {
        searchableAlbumFlow().collect {
            // 从数据库中检索已经索引的相册
            // 有些相册可能已经索引但已被删除，因此要从全部相册中筛选，而不能直接返回数据库的结果
            searchableAlbumList.clear()
            searchableAlbumList.addAll(it)
            // 从全部相册减去已经索引的ID，就是未索引的相册
            val unsearchable = albumList.filter { all -> !it.contains(all) }
            unsearchableAlbumList.clear()
            unsearchableAlbumList.addAll(unsearchable)
        }
    }

    private fun initAllAlbumList() {
        viewModelScope.launch(Dispatchers.IO) {
            // 本机中的相册
            val albums = albumRepository.getAllAlbums()
            albumList.addAll(albums)
            Log.d(TAG, "ALL albums: ${albums.size}")

            // 从数据库中检索已经索引的相册
            // 有些相册可能已经索引但已被删除，因此要从全部相册中筛选，而不能直接返回数据库的结果
            val searchable =
                albumRepository.getSearchableAlbums().filter { albums.contains(it) }
            searchableAlbumList.addAll(searchable)
            // 从全部相册减去已经索引的ID，就是未索引的相册
            val unsearchable = albums.filter { !searchable.contains(it) }
            unsearchableAlbumList.addAll(unsearchable)
            initDataFlow()
        }
    }

    private val _searchScreenState = MutableStateFlow(
        SearchScreenState(
            emptyList(), emptyList(), emptyList(), 0, 0.0F
        )
    )
    val searchScreenState = _searchScreenState.asStateFlow()

    fun encodeAlbum(album: Album) {
        _searchScreenState.value = _searchScreenState.value.copy(
            currentProgress = 0.0F,
            currentId = album.id
        )
        viewModelScope.launch(Dispatchers.Default) {
            val photos = photoRepository.getPhotoListByAlbumId(album.id)
            Log.d(TAG, photos.size.toString())
            val imageSearcher = ImageSearcher
            val success = imageSearcher.encodePhotoList(photos) { cur, total ->
//                _progressState.update { (cur.toFloat() / total) }
                _searchScreenState.value = _searchScreenState.value.copy(
                    currentProgress = (cur.toFloat() / total)
                )
            }
            if (success) {
                // 等待完全Encode完毕之后，再向数据库添加一条记录，表示该album已被索引
                albumRepository.addSearchableAlbum(album)
                Looper.prepare()
                showToast("完成！${album.label}")
                Looper.loop()
                delay(1000)
                initAllAlbumList()
            } else {
                Log.w(TAG, "encodePhotoList failed! Maybe too much request.")
            }
        }
    }

    fun toSearchResult(context: Context, text: String) {
        val intent = Intent(context, SearchResultActivity::class.java)
        intent.apply {
            putExtra("text", text)
        }
        context.startActivity(intent)
    }

    fun toDevTest(context: Context) {
        val intent = Intent(context, DevActivity::class.java)
        context.startActivity(intent)
    }
}

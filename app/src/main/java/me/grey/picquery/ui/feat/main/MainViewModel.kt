package me.grey.picquery.ui.feat.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.core.ImageSearcher
import me.grey.picquery.core.encoder.ImageEncoder
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album

class MainViewModel : ViewModel() {

    val albumList: LiveData<List<Album>>
        get() = _albumList

    val searchableAlbumList: LiveData<List<Album>>
        get() = _searchableAlbumList
    val unsearchableAlbumList: LiveData<List<Album>>
        get() = _unsearchableAlbumList

    private val _albumList = MutableLiveData<List<Album>>()

    private val _searchableAlbumList = MutableLiveData<List<Album>>()
    private val _unsearchableAlbumList = MutableLiveData<List<Album>>()

    private val albumRepository = AlbumRepository(PicQueryApplication.context.contentResolver)
    private val photoRepository = PhotoRepository(PicQueryApplication.context.contentResolver)

    init {
        _albumList.value = emptyList()
        initAllAlbumList()
    }

    private fun initAllAlbumList() {
        viewModelScope.launch(Dispatchers.IO) {
            // 本机中的相册
            val albums = albumRepository.getAllAlbums()
            _albumList.postValue(albums)

            // 从数据库中检索已经索引的相册
            // 有些相册可能已经索引但已被删除，因此要从全部相册中筛选，而不能直接返回数据库的结果
            val searchable = albumRepository.getSearchableAlbums().filter { albums.contains(it) }
            _searchableAlbumList.postValue(searchable)
            // 从全部相册减去已经索引的ID，就是未索引的相册
            val unsearchable = albums.filter { !searchable.contains(it) }
            _unsearchableAlbumList.postValue(unsearchable)
        }
    }

    fun encodeAlbum(album: Album) {
        viewModelScope.launch {
            val photos = photoRepository.getPhotoListByAlbumId(album.id)
            val imageSearcher = ImageSearcher
        }
    }
}
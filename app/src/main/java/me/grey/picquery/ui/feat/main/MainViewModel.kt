package me.grey.picquery.ui.feat.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.data.AlbumRepository
import me.grey.picquery.data.model.Album

class MainViewModel : ViewModel() {

    val albumList: LiveData<List<Album>>
        get() = _albumList
    val indexedAlbumList: LiveData<List<Album>>
        get() = _indexedAlbumList

    private val _albumList = MutableLiveData<List<Album>>()
    private val _indexedAlbumList = MutableLiveData<List<Album>>()

    init {
        _albumList.value = emptyList()
        initAllAlbumList()
        initIndexedAlbumList()
    }

    private fun initAllAlbumList() {
        viewModelScope.launch(Dispatchers.IO) {
            val albumRepository = AlbumRepository(PicQueryApplication.context.contentResolver)
            val albums = albumRepository.getAlbums()
            _albumList.postValue(albums)
        }
    }

    private fun initIndexedAlbumList() {
        viewModelScope.launch(Dispatchers.IO) {
            val albumRepository = AlbumRepository(PicQueryApplication.context.contentResolver)
            val albums = albumRepository.getAlbums()
            _indexedAlbumList.postValue(albums)
        }
    }
}
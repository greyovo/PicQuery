package me.grey.picquery.ui.search

import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.common.showToast
import me.grey.picquery.core.ImageSearcher
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo

enum class SearchState {
    NO_INDEX, // 没有索引
    LOADING, // 初始化加载模型中
    READY,  // 准备好搜索
    SEARCHING,  // 正在搜索
    FINISHED,  // 搜索已完成
}

class SearchViewModel : ViewModel() {
    companion object {
        private const val TAG = "SearchResultViewModel"
    }

    private val _resultList = MutableStateFlow<List<Photo>>(emptyList())
    val resultList = _resultList.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState.READY)
    val searchState = _searchState.asStateFlow()

    val searchText = mutableStateOf("")
    val searchRange = mutableStateListOf<Album>()

    private val repo = PhotoRepository(PicQueryApplication.context.contentResolver)
    fun startSearch(text: String, albumRange: List<Album> = emptyList()) {
        if (text.trim().isEmpty()) {
            showToast("请输入搜索内容")
            Log.w(TAG, "搜索字段为空")
            return
        }
        searchText.value = text
        viewModelScope.launch(Dispatchers.IO) {
            _searchState.value = SearchState.SEARCHING
            val ids = ImageSearcher.search(text)
            if (ids != null) {
                _resultList.value = repo.getPhotoListByIds(ids)
            }
            _searchState.value = SearchState.FINISHED
        }
    }

    val isFilterOpen = mutableStateOf(false)
    fun openFilterBottomSheet() {
        isFilterOpen.value = true
    }

    @OptIn(ExperimentalMaterial3Api::class)
    suspend fun closeFilterBottomSheet(bottomSheetState: SheetState) {
        bottomSheetState.hide()
        isFilterOpen.value = false
    }

    fun clearAll() {
        searchText.value = ""
        _resultList.value = emptyList()
        _searchState.value = SearchState.READY
    }

    fun toggleToRange(album: Album) {
        if (!searchRange.contains(album)) {
            searchRange.add(album)
        } else {
            searchRange.remove(album)
        }
    }

    fun addAllToRange(list: List<Album>) {
        searchRange.clear()
        searchRange.addAll(list)
    }

    fun removeAllFromRange(list: List<Album>) {
        searchRange.clear()
    }
}
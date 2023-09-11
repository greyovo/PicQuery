package me.grey.picquery.ui.search

import android.util.Log
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
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

enum class SearchState() {
    UNREADY, // 没有索引/没有可供搜索的
    LOADING, // 初始化加载模型中
    READY,  // 准备好搜索
    SEARCHING,  // 正在搜索
    FINISHED,  // 搜索已完成
    ;

    val searching: Boolean
        get() {
            return this == SEARCHING
        }
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

    fun clearAll() {
        searchText.value = ""
        _resultList.value = emptyList()
        _searchState.value = SearchState.READY
    }
}
package me.grey.picquery.ui.search

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.R
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher

enum class SearchState {
    NO_INDEX, // 没有索引
    LOADING, // 初始化加载模型中
    READY,  // 准备好搜索
    SEARCHING,  // 正在搜索
    FINISHED,  // 搜索已完成
}

class SearchViewModel(
    private val imageSearcher: ImageSearcher,
    private val albumManager: AlbumManager,
) : ViewModel() {
    companion object {
        private const val TAG = "SearchResultViewModel"
    }

    private val _resultList = MutableStateFlow<List<Photo>>(emptyList())
    val resultList = _resultList.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState.LOADING)
    val searchState = _searchState.asStateFlow()

    val searchText = mutableStateOf("")
    val isSearchRangeAll = mutableStateOf(true)
    val searchRange = mutableStateListOf<Album>()

    private val context: Context
        get() {
            return PicQueryApplication.context
        }

    private val repo = PhotoRepository(context.contentResolver)

    private var initialized = false

    init {
        Log.d(TAG, "init!!! SearchViewModel")
    }

    fun startSearch(text: String) {
        if (text.trim().isEmpty()) {
            showToast(context.getString(R.string.empty_search_content_toast))
            Log.w(TAG, "搜索字段为空")
            return
        }
        searchText.value = text
        viewModelScope.launch(Dispatchers.Default) {
            _searchState.value = SearchState.SEARCHING
            imageSearcher.searchWithChinese(text) { ids ->
                if (ids != null) {
                    _resultList.value = repo.getPhotoListByIds(ids)
                }
                _searchState.value = SearchState.FINISHED
            }
        }
    }

    fun clearAll() {
        searchText.value = ""
        _resultList.value = emptyList()
        _searchState.value = SearchState.READY
    }


//    fun displayPhotoFullscreen(context: Context, index: Int, photo: Photo) {
//        val intent = Intent(context, DisplayActivity::class.java)
//        intent.putExtra("index", index)
//        intent.putExtra("id", photo.id)
//        context.startActivity(intent)
//    }
}
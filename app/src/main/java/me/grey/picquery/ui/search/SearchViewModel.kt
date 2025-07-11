package me.grey.picquery.ui.search

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.R
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.ImageSearcher
import timber.log.Timber

enum class SearchState {
    NO_INDEX, // 没有索引
    LOADING, // 初始化加载模型中
    READY,  // 准备好搜索
    SEARCHING,  // 正在搜索
    FINISHED,  // 搜索已完成
}

class SearchViewModel(
    private val imageSearcher: ImageSearcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val repo: PhotoRepository
) : ViewModel() {
    companion object {
        private const val TAG = "SearchResultViewModel"
    }

    private val _resultList = MutableStateFlow<List<Photo>>(emptyList())
    val resultList = _resultList.asStateFlow()
    private val _resultMap = MutableStateFlow<Map<Long, Double>>(mutableMapOf())
    val resultMap: StateFlow<Map<Long, Double>> = _resultMap.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState.LOADING)
    val searchState = _searchState.asStateFlow()

    private val _searchText = MutableStateFlow<String>("")
    val searchText: StateFlow<String> = _searchText.map { it }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ""
    )

    private val context: Context
        get() {
            return PicQueryApplication.context
        }

    init {
        Log.d(TAG, "init!!! SearchViewModel")
    }

    fun onQueryChange(query: String) {
        _searchState.value = SearchState.READY
        _searchText.value = query
    }

    fun startSearch(text: String) {
        if (text.trim().isEmpty()) {
            showToast(context.getString(R.string.empty_search_content_toast))
            Timber.tag(TAG).w("搜索字段为空")
            return
        }
        _searchText.value = text
        viewModelScope.launch(ioDispatcher) {
            _searchState.value = SearchState.SEARCHING
            imageSearcher.searchV2(text) { ids ->
                Timber.tag(TAG).d("searchV2 ids: $ids")
                if (ids.isNotEmpty()) {

                    val photos = repo.getPhotoListByIds(ids.map { it.first })
                    _resultList.value = reOrderList(photos, ids.map { it.first })
                    _resultMap.update {
                        ids.associate { it.first to (1.0-it.second) }.toMutableMap()
                    }
                    Timber.tag(TAG).d("searchV2 photos re-orders: ${_resultList.value.size}")

                }
                _searchState.value = SearchState.FINISHED
            }
        }
    }

    fun startSearch(uri: Uri) {
        // 从 uri 获取图片
        val photo = repo.getBitmapFromUri(uri)
        if (photo == null) {
            showToast(context.getString(R.string.empty_search_content_toast))
            Log.w(TAG, "搜索字段为空")
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _searchState.value = SearchState.SEARCHING
            imageSearcher.searchWithRangeV2(photo) { ids ->
                if (ids.isNotEmpty()) {

                    val photos = repo.getPhotoListByIds(ids.map { it.first })
                    _resultList.value = reOrderList(photos, ids.map { it.first })
                    _resultMap.update {
                        ids.associate { it.first to (1.0-it.second) }.toMutableMap()
                    }
                    Timber.tag(TAG).d("searchV2 photos re-orders: ${_resultList.value.size}")

                }
                _searchState.value = SearchState.FINISHED
            }
        }
    }

    // fix the order of the result list
    private fun reOrderList(originalList: List<Photo>, orderList: List<Long>): List<Photo> {
        val photoMap = originalList.associateBy { it.id }
        return orderList.mapNotNull { id -> photoMap[id] }
    }
}
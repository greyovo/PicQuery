package me.grey.picquery.ui.home

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.grey.picquery.domain.ImageSearcher
import timber.log.Timber

data class UserGuideTaskState(
    val permissionDone: Boolean = false,
    val indexDone: Boolean = false
) {
    val allFinished: Boolean
        get() = permissionDone && indexDone
}

class HomeViewModel(
    private val imageSearcher: ImageSearcher,
    private val preferenceRepository: me.grey.picquery.data.data_source.PreferenceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText

    val userGuideVisible = mutableStateOf(false)

    val currentGuideState = mutableStateOf(UserGuideTaskState())

    fun onQueryChange(query: String) {
        _searchText.value = query
    }

    init {
        viewModelScope.launch {
            // 检查用户是否已经完成过引导
            val guideCompleted = preferenceRepository.isUserGuideCompleted()
            val hasData = imageSearcher.hasEmbedding()
            
            if (guideCompleted || hasData) {
                // 用户已经完成引导或有索引数据，不需要显示引导
                currentGuideState.value = UserGuideTaskState(
                    permissionDone = true,
                    indexDone = true
                )
                userGuideVisible.value = false
                
                // 如果有数据但标记未设置，更新标记
                if (hasData && !guideCompleted) {
                    preferenceRepository.setUserGuideCompleted(true)
                }
            } else {
                // 首次使用，需要显示引导
                userGuideVisible.value = true
            }
        }
    }

    fun showUserGuide() {
        userGuideVisible.value = true
    }

    fun doneRequestPermission() {
        Timber.tag(TAG).d("doneRequestPermission")
        currentGuideState.value = currentGuideState.value.copy(permissionDone = true)
    }

    fun doneIndexAlbum() {
        currentGuideState.value = currentGuideState.value.copy(indexDone = true)
    }

    fun finishGuide() {
        userGuideVisible.value = false
        // 标记用户已完成引导
        viewModelScope.launch {
            preferenceRepository.setUserGuideCompleted(true)
        }
    }
}

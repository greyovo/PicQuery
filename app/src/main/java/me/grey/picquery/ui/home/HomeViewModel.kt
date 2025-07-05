package me.grey.picquery.ui.home

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.grey.picquery.domain.ImageSearcher

data class UserGuideTaskState(
    val permissionDone: Boolean = false,
    val indexDone: Boolean = false
) {
    val allFinished: Boolean
        get() = permissionDone && indexDone
}

class HomeViewModel(
    private val imageSearcher: ImageSearcher
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
            if (!imageSearcher.hasEmbedding()) {
                userGuideVisible.value = true
            } else {
                currentGuideState.value = currentGuideState.value.copy(indexDone = true)
            }
        }
    }

    fun showUserGuide() {
        userGuideVisible.value = true
    }

    fun doneRequestPermission() {
        Log.d(TAG, "doneRequestPermission")
        currentGuideState.value = currentGuideState.value.copy(permissionDone = true)
    }

    fun doneIndexAlbum() {
        currentGuideState.value = currentGuideState.value.copy(indexDone = true)
    }

    fun finishGuide() {
        userGuideVisible.value = false
    }
}

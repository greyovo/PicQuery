package me.grey.picquery.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher

data class UserGuideTaskState(
    val permissionDone: Boolean = false,
    val indexDone: Boolean = false,
) {
    val allFinished: Boolean
        get() = permissionDone && indexDone
}

class HomeViewModel(
    private val imageSearcher: ImageSearcher,
    private val albumManager: AlbumManager,
) : ViewModel() {

    val searchText = mutableStateOf("")

    val showUserGuide = mutableStateOf(false)

    val currentGuideState = mutableStateOf(UserGuideTaskState())

    init {
        viewModelScope.launch {
            if (!imageSearcher.hasEmbedding()) {
                showUserGuide.value = true
            } else {
                currentGuideState.value =
                    UserGuideTaskState(permissionDone = true, indexDone = true)
            }
        }
    }

    fun doneRequestPermission() {
        currentGuideState.value = currentGuideState.value.copy(permissionDone = true)
    }

    fun doneIndexAlbum() {
        currentGuideState.value = currentGuideState.value.copy(indexDone = true)
    }

    fun finishGuide() {
        showUserGuide.value = false
    }

    /**
     * 导航
     * */
    fun navigateToSearchScreen(navController: NavController) {

    }

    fun navigateToSetting(navController: NavController) {}
}
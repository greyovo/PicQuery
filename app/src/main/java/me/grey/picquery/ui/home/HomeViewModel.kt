package me.grey.picquery.ui.home

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher

class HomeViewModel(
    private val imageSearcher: ImageSearcher,
    private val albumManager: AlbumManager,
) : ViewModel() {

    val searchText = mutableStateOf("")

    val showUserGuide = mutableStateOf(false)

    val currentGuideStep = mutableIntStateOf(1)

    init {
        viewModelScope.launch {
            if (!imageSearcher.hasEmbedding()) {
                showUserGuide.value = true
            }
        }
    }

    fun doneRequestPermission() {
        currentGuideStep.intValue = 2
        viewModelScope.launch {
            if (imageSearcher.hasEmbedding()) {
                finishGuide()
            }
        }
    }

    fun finishGuide() {
        currentGuideStep.intValue = 3
    }

    /**
     * 导航
     * */
    fun navigateToSearchScreen(navController: NavController) {

    }

    fun navigateToSetting(navController: NavController) {}
}
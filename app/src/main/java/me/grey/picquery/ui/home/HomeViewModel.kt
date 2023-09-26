package me.grey.picquery.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher

class HomeViewModel(
    private val imageSearcher: ImageSearcher,
    private val albumManager: AlbumManager,
) : ViewModel() {

    val searchText = mutableStateOf("")

    // TODO 索引相册按钮
    // TODO 启动搜索
    // TODO 导航到设置页

    /**
     * 导航
     * */
    fun navigateToSearchScreen(navController: NavController) {

    }

    fun navigateToSetting(navController: NavController) {}
}
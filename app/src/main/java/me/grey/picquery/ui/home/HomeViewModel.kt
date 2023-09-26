package me.grey.picquery.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.domain.ImageSearcher

class HomeViewModel(
    private val imageSearcher: ImageSearcher,
    private val albumManager: AlbumManager,
) : ViewModel() {

    val searchText = mutableStateOf("")

    init {
        albumManager
    }

    // TODO 索引相册按钮
    // TODO 搜索栏点击的事件处理、开始搜索（导航到SearchScreen）
    // TODO 启动搜索
    // TODO 导航到设置页
    fun onManageAlbum() {
        albumManager.openBottomSheet()
    }

    fun navigateToSearchScreen() {}

    /**
     * 其他导航
     * */
    fun navigateToSetting() {}
}
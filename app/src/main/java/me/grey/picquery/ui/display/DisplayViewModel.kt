package me.grey.picquery.ui.display

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.ImageSearcher

class DisplayViewModel(
    private val photoRepository: PhotoRepository,
    private val imageSearcher: ImageSearcher,
) : ViewModel() {

    val photoList = mutableStateListOf<Photo>()
    val currentIndex = mutableIntStateOf(0)

    // FIXME 测试用
    fun loadPhotos(initialPage: Int) {
        currentIndex.intValue = initialPage
        viewModelScope.launch {
            val ids = imageSearcher.searchResultIds
            photoList.addAll(photoRepository.getPhotoListByIds(ids))
        }
    }
}
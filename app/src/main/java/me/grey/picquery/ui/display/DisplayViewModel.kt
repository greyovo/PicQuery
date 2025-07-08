package me.grey.picquery.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.ImageSearcher

class DisplayViewModel(
    private val photoRepository: PhotoRepository,
    private val imageSearcher: ImageSearcher
) : ViewModel() {

    private val _photoList = MutableStateFlow<MutableList<Photo>>(mutableListOf())
    val photoList: StateFlow<MutableList<Photo>> = _photoList

    fun loadPhotos() {
        viewModelScope.launch {
            val ids = imageSearcher.searchResultIds
            val list = reorderList(photoRepository.getPhotoListByIds(ids), ids)
            _photoList.emit(list.toMutableList())
        }
    }

    private fun reorderList(originalList: List<Photo>, orderList: List<Long>): List<Photo> {
        val photoMap = originalList.associateBy { it.id }
        return orderList.mapNotNull { id -> photoMap[id] }
    }
}

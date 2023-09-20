package me.grey.picquery.ui.display

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo

class DisplayViewModel : ViewModel() {

    val photoList = mutableStateListOf<Photo>()

    private val photoRepository = PhotoRepository(PicQueryApplication.context.contentResolver)

    // FIXME 测试用
    fun findPhotoById(id: Long) {
        viewModelScope.launch {
            val p = photoRepository.getPhotoById(id)
            if (p != null) {
                photoList.add(p)
            }
        }
    }
}
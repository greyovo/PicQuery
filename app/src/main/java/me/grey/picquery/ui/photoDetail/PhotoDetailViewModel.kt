package me.grey.picquery.ui.photoDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.SimilarityManager

class PhotoDetailViewModel(
    private val similarityManager: SimilarityManager,
    private val photoRepository: PhotoRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _photoList = MutableStateFlow<List<Photo>>(emptyList())
    val photoList: StateFlow<List<Photo>> = _photoList

    fun loadPhotosFromGroup(groupIndex: Int) {
        viewModelScope.launch(dispatcher) {
            val similarGroup = similarityManager.getSimilarityGroupByIndex(groupIndex)

            if (similarGroup != null) {
                val photos = similarGroup.mapNotNull { photoNode ->
                    photoRepository.getPhotoById(photoNode.photoId)
                }

                _photoList.update { photos }
            } else {
                similarityManager.groupSimilarPhotos().collect {
                    val updatedSimilarGroup = similarityManager.getSimilarityGroupByIndex(
                        groupIndex
                    )
                    if (updatedSimilarGroup != null) {
                        val photos = updatedSimilarGroup.mapNotNull { photoNode ->
                            photoRepository.getPhotoById(photoNode.photoId)
                        }

                        _photoList.update { photos }
                        return@collect
                    }
                }
            }
        }
    }
}

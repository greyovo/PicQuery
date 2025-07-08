package me.grey.picquery.ui.simlilar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.SimilarityManager
import timber.log.Timber

enum class ErrorType {
    WORKER_TIMEOUT, CALCULATION_FAILED, NO_SIMILAR_PHOTOS, UNKNOWN
}

// Sealed interface for Similar Photos UI State
sealed interface SimilarPhotosUiState {
    data object Loading : SimilarPhotosUiState
    data object Empty : SimilarPhotosUiState
    data class Error(
        val type: ErrorType = ErrorType.UNKNOWN,
        val message: String? = null
    ) : SimilarPhotosUiState

    data class Success(val similarPhotoGroups: List<List<Photo>>) : SimilarPhotosUiState
}

class SimilarPhotosViewModel(
    private val coroutineScope: CoroutineDispatcher,
    private val photoRepository: PhotoRepository,
    private val objectBoxEmbeddingRepository: ObjectBoxEmbeddingRepository,
    private val similarityManager: SimilarityManager,
    private val workManager: WorkManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<SimilarPhotosUiState>(SimilarPhotosUiState.Loading)
    val uiState: StateFlow<SimilarPhotosUiState> = _uiState
    val similarPhotoIds = mutableSetOf<Long>()

    private val _selectedPhotos = MutableStateFlow<MutableList<Photo>>(mutableListOf<Photo>())
    val selectedPhotos = _selectedPhotos.asStateFlow()

    fun getPhotosFromGroup(groupIndex: Int) {
        uiState.value.let { state ->
            val photos = if (state is SimilarPhotosUiState.Success) {
                state.similarPhotoGroups.getOrNull(groupIndex) ?: emptyList()
            } else {
                emptyList()
            }
            _selectedPhotos.update { photos.toMutableList() }
        }
    }

    fun findSimilarPhotos() = viewModelScope.launch {
        Timber.tag("SimilarPhotosViewModel")
            .d("start findSimilarPhotos${System.currentTimeMillis()}")
        val processedPhotoIds = ConcurrentHashMap.newKeySet<Long>()
        val similarGroups = mutableListOf<List<Photo>>()

        objectBoxEmbeddingRepository.getAllEmbeddingsPaginated(2000).collect { embeddingBatch ->
            // 使用 Flow 处理批次
            flow {
                embeddingBatch
                    // 过滤未处理的照片
                    .filter { embedding ->
                        embedding.photoId !in processedPhotoIds
                    }.forEach { baseEmbedding ->
                        // 防止重复处理
                        processedPhotoIds.add(baseEmbedding.photoId)

                        // 查找相似图片
                        val similarEmbeddings =
                            objectBoxEmbeddingRepository.findSimilarEmbeddings(
                                queryVector = baseEmbedding.data,
                                topK = 30,
                                similarityThreshold = 0.95f
                            )

                        // 获取照片并标记为已处理
                        val photos =
                            photoRepository.getPhotoListByIds(
                                similarEmbeddings.map { it.get().photoId }
                            )

                        // 标记为已处理
                        processedPhotoIds.addAll(similarEmbeddings.map { it.get().photoId })

                        // 只发出超过1张的相似组
                        if (photos.size > 1) {
                            emit(photos)
                        }
                    }
            }.flowOn(Dispatchers.IO).onCompletion {
                Timber.tag("SimilarPhotosViewModel")
                    .d("end findSimilarPhotos${System.currentTimeMillis()}")
            }.collect { similarPhotos ->
                // 去重
                val uniqueSimilarPhotos = similarPhotos.distinctBy { it.id }

                // 仅添加唯一的相似组
                if (uniqueSimilarPhotos.size > 1) {
                    val existingGroup = similarGroups.find {
                        it.map { photo -> photo.id }
                            .intersect(uniqueSimilarPhotos.map { it.id }.toSet())
                            .isNotEmpty()
                    }

                    if (existingGroup == null) {
                        similarGroups.add(uniqueSimilarPhotos)
                        _uiState.update {
                            SimilarPhotosUiState.Success(similarGroups.toList())
                        }
                    }
                }
            }
        }
    }

    fun resetState() {
        // Clear any cached data
        similarityManager.clearCachedGroups()
        // Reset UI state to Loading
        _uiState.update { SimilarPhotosUiState.Loading }
    }
}

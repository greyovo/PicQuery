package me.grey.picquery.ui.simlilar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.SimilarityManager
import me.grey.picquery.domain.worker.ImageSimilarityCalculationWorker
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap


enum class ErrorType {
    WORKER_TIMEOUT, CALCULATION_FAILED, NO_SIMILAR_PHOTOS, UNKNOWN
}

// Sealed interface for Similar Photos UI State
sealed interface SimilarPhotosUiState {
    data object Loading : SimilarPhotosUiState
    data object Empty : SimilarPhotosUiState
    data class Error(
        val type: ErrorType = ErrorType.UNKNOWN, val message: String? = null
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
            val photos =  if (state is SimilarPhotosUiState.Success) {
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
                                photoRepository.getPhotoListByIds(similarEmbeddings.map { it.get().photoId })

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

    // Add a method to update similarity configuration
    fun updateSimilarityConfiguration(
        searchImageSimilarityThreshold: Float = 0.96f,
        similarityDelta: Float = 0.02f,
        minSimilarityGroupSize: Int = 2
    ) {
        Timber.tag("updateSimilarityConfiguration").d("updateSimilarityConfiguration")
        // Update SimilarityManager configuration
        similarityManager.updateConfiguration(
            newSimilarityThreshold = searchImageSimilarityThreshold,
            newSimilarityDelta = similarityDelta,
            newMinGroupSize = minSimilarityGroupSize
        )
        // Reset UI state to trigger recalculation
        _uiState.update { SimilarPhotosUiState.Loading }
        // Reload similar photos with new configuration
        loadSimilarPhotos()
    }

    private fun calculateSimilarities(): kotlinx.coroutines.flow.Flow<WorkInfo.State> {
        val similarityCalculationWork =
            OneTimeWorkRequestBuilder<ImageSimilarityCalculationWorker>().build()
        workManager.enqueue(similarityCalculationWork)

        return workManager.getWorkInfoByIdFlow(similarityCalculationWork.id).transform { workInfo ->
                Timber.tag("SimilarPhotosViewModel").d("Worker state: ${workInfo.state}")
                emit(workInfo.state)
                if (workInfo.state.isFinished) return@transform
            }
    }

    fun loadSimilarPhotos() {
        viewModelScope.launch(coroutineScope) {
            _uiState.update { SimilarPhotosUiState.Loading }
            try {
                // Wait for worker to complete with a timeout
                withTimeout(30_000) {
                    calculateSimilarities().first { it == WorkInfo.State.SUCCEEDED || it == WorkInfo.State.FAILED }
                }
            } catch (e: Exception) {
                Timber.tag("SimilarPhotosViewModel")
                    .e(e, "Error waiting for similarity calculation")
                _uiState.update {
                    SimilarPhotosUiState.Error(
                        type = ErrorType.WORKER_TIMEOUT,
                        message = "Similarity calculation timed out after 30 seconds"
                    )
                }
                return@launch
            }

            val similarGroups = mutableListOf<List<Photo>>()
            val start = System.currentTimeMillis()
            similarityManager.groupSimilarPhotos().catch { e ->
                    Timber.tag("SimilarPhotosViewModel").e(e, "Error loading similar photos")
                    _uiState.update {
                        SimilarPhotosUiState.Error(
                            type = ErrorType.CALCULATION_FAILED,
                            message = e.localizedMessage ?: "Failed to group similar photos"
                        )
                    }
                }.onCompletion {
                    val end = System.currentTimeMillis()
                    Timber.tag("SimilarPhotosViewModel").d("%sms", (end - start).toString())
                }.collect { similarGroup ->
                    val photoGroup = similarGroup.mapNotNull { node ->
                        photoRepository.getPhotoById(node.photoId)
                    }

                    if (photoGroup.isNotEmpty()) {
                        similarGroups.add(photoGroup)
                        _uiState.update { SimilarPhotosUiState.Success(similarGroups.toList()) }
                    }
                }

            if (similarGroups.isEmpty()) {
                Timber.tag("SimilarPhotosViewModel").d("No similar photos found")
                _uiState.update {
                    SimilarPhotosUiState.Error(
                        type = ErrorType.NO_SIMILAR_PHOTOS,
                        message = "No similar photos could be found"
                    )
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
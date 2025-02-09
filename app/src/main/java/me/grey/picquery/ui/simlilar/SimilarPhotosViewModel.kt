package me.grey.picquery.ui.simlilar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Photo
import me.grey.picquery.domain.SimilarityManager
import me.grey.picquery.domain.worker.ImageSimilarityCalculationWorker
import timber.log.Timber


enum class ErrorType {
    WORKER_TIMEOUT,
    CALCULATION_FAILED,
    NO_SIMILAR_PHOTOS,
    UNKNOWN
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
    private val similarityManager: SimilarityManager,
    private val workManager: WorkManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<SimilarPhotosUiState>(SimilarPhotosUiState.Loading)
    val uiState: StateFlow<SimilarPhotosUiState> = _uiState

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

        return workManager.getWorkInfoByIdFlow(similarityCalculationWork.id)
            .transform { workInfo ->
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
                    calculateSimilarities()
                        .first { it == WorkInfo.State.SUCCEEDED || it == WorkInfo.State.FAILED }
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
            similarityManager.groupSimilarPhotos()
                .catch { e ->
                    Timber.tag("SimilarPhotosViewModel").e(e, "Error loading similar photos")
                    _uiState.update {
                        SimilarPhotosUiState.Error(
                            type = ErrorType.CALCULATION_FAILED,
                            message = e.localizedMessage ?: "Failed to group similar photos"
                        )
                    }
                }
                .onCompletion {
                    val end = System.currentTimeMillis()
                    Timber.tag("SimilarPhotosViewModel").d("%sms", (end - start).toString())
                }
                .collect { similarGroup ->
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
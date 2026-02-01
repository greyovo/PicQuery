package me.grey.picquery.domain

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Similarity Configuration Service - Responsible for managing similarity grouping parameters
 *
 * Responsibilities:
 * - Manage similarity threshold
 * - Manage similarity delta
 * - Manage minimum group size
 * - Persist configuration to storage
 * - Update GroupSimilarPhotosUseCase when configuration changes
 */
class SimilarityConfigurationService(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SimilarityConfigService"

        const val DEFAULT_SIMILARITY_THRESHOLD = 0.96f
        const val DEFAULT_SIMILARITY_DELTA = 0.02f
        const val DEFAULT_MIN_GROUP_SIZE = 2

        // Threshold boundaries
        const val MIN_THRESHOLD = 0.80f
        const val MAX_THRESHOLD = 0.99f

        // Delta boundaries
        const val MIN_DELTA = 0.01f
        const val MAX_DELTA = 0.10f

        // Group size boundaries
        const val MIN_GROUP_SIZE = 2
        const val MAX_GROUP_SIZE = 10
    }

    private val _similarityThreshold = mutableFloatStateOf(DEFAULT_SIMILARITY_THRESHOLD)
    val similarityThreshold: State<Float> = _similarityThreshold

    private val _similarityDelta = mutableFloatStateOf(DEFAULT_SIMILARITY_DELTA)
    val similarityDelta: State<Float> = _similarityDelta

    private val _minGroupSize = mutableIntStateOf(DEFAULT_MIN_GROUP_SIZE)
    val minGroupSize: State<Int> = _minGroupSize

    private var isInitialized = false

    /**
     * Initialize configuration - Load saved configuration from storage
     * Note: For future implementation - add persistence layer
     */
    suspend fun initialize() {
        if (isInitialized) {
            Timber.tag(TAG).d("Already initialized, skipping")
            return
        }

        // Future: Load from PreferenceRepository or DataStore
        // val (savedThreshold, savedDelta, savedMinGroupSize) = preferenceRepository.loadSimilarityConfiguration()

        isInitialized = true
        Timber.tag(TAG).d(
            "Configuration initialized: threshold=$DEFAULT_SIMILARITY_THRESHOLD, " +
            "delta=$DEFAULT_SIMILARITY_DELTA, minGroupSize=$DEFAULT_MIN_GROUP_SIZE"
        )
    }

    /**
     * Update similarity configuration
     *
     * @param newSimilarityThreshold New similarity threshold (will be coerced to 0.80-0.99 range)
     * @param newSimilarityDelta New similarity delta (will be coerced to 0.01-0.10 range)
     * @param newMinGroupSize New minimum group size (will be coerced to 2-10 range)
     */
    fun updateConfiguration(
        newSimilarityThreshold: Float? = null,
        newSimilarityDelta: Float? = null,
        newMinGroupSize: Int? = null
    ) {
        newSimilarityThreshold?.let {
            _similarityThreshold.floatValue = it.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        }
        newSimilarityDelta?.let {
            _similarityDelta.floatValue = it.coerceIn(MIN_DELTA, MAX_DELTA)
        }
        newMinGroupSize?.let {
            _minGroupSize.intValue = it.coerceIn(MIN_GROUP_SIZE, MAX_GROUP_SIZE)
        }

        // Future: Asynchronously save to DataStore
        // scope.launch {
        //     preferenceRepository.saveSimilarityConfiguration(
        //         _similarityThreshold.floatValue,
        //         _similarityDelta.floatValue,
        //         _minGroupSize.intValue
        //     )
        // }

        Timber.tag(TAG).d(
            "Configuration updated: " +
            "similarityThreshold=${_similarityThreshold.floatValue}, " +
            "similarityDelta=${_similarityDelta.floatValue}, " +
            "minGroupSize=${_minGroupSize.intValue}"
        )
    }

    /**
     * Reset to default configuration
     */
    fun resetToDefaults() {
        updateConfiguration(
            DEFAULT_SIMILARITY_THRESHOLD,
            DEFAULT_SIMILARITY_DELTA,
            DEFAULT_MIN_GROUP_SIZE
        )
        Timber.tag(TAG).d("Configuration reset to defaults")
    }

    /**
     * Get current similarity threshold
     */
    fun getSimilarityThreshold(): Float = _similarityThreshold.floatValue

    /**
     * Get current similarity delta
     */
    fun getSimilarityDelta(): Float = _similarityDelta.floatValue

    /**
     * Get current minimum group size
     */
    fun getMinGroupSize(): Int = _minGroupSize.intValue
}

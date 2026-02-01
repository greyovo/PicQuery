package me.grey.picquery.domain

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.grey.picquery.data.data_source.PreferenceRepository
import timber.log.Timber

/**
 * Search Configuration Service - Responsible for managing search parameters
 *
 * Responsibilities:
 * - Manage match threshold
 * - Manage top K results count
 * - Persist configuration to DataStore
 * - Load configuration on initialization
 */
class SearchConfigurationService(
    private val preferenceRepository: PreferenceRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SearchConfigService"

        const val DEFAULT_MATCH_THRESHOLD = 0.20f
        const val DEFAULT_TOP_K = 30

        // Threshold boundaries
        const val MIN_THRESHOLD = 0.1f
        const val MAX_THRESHOLD = 0.5f

        // TopK boundaries
        const val MIN_TOP_K = 10
        const val MAX_TOP_K = 100
    }

    private val _matchThreshold = mutableFloatStateOf(DEFAULT_MATCH_THRESHOLD)
    val matchThreshold: State<Float> = _matchThreshold

    private val _topK = mutableIntStateOf(DEFAULT_TOP_K)
    val topK: State<Int> = _topK

    private var isInitialized = false

    /**
     * Initialize configuration - Load saved configuration from DataStore
     */
    suspend fun initialize() {
        if (isInitialized) {
            Timber.tag(TAG).d("Already initialized, skipping")
            return
        }

        val (savedThreshold, savedTopK) = preferenceRepository.loadSearchConfigurationSync()
        _matchThreshold.floatValue = savedThreshold
        _topK.intValue = savedTopK
        isInitialized = true

        Timber.tag(TAG).d(
            "Configuration loaded: matchThreshold=$savedThreshold, topK=$savedTopK"
        )
    }

    /**
     * Update search configuration
     *
     * @param newMatchThreshold New match threshold (will be coerced to 0.1-0.5 range)
     * @param newTopK New top K count (will be coerced to 10-100 range)
     */
    fun updateConfiguration(newMatchThreshold: Float, newTopK: Int) {
        _matchThreshold.floatValue = newMatchThreshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        _topK.intValue = newTopK.coerceIn(MIN_TOP_K, MAX_TOP_K)

        // Asynchronously save to DataStore
        scope.launch {
            preferenceRepository.saveSearchConfiguration(
                _matchThreshold.floatValue,
                _topK.intValue
            )
        }

        Timber.tag(TAG).d(
            "Configuration updated: matchThreshold=${_matchThreshold.floatValue}, topK=${_topK.intValue}"
        )
    }

    /**
     * Get current match threshold
     */
    fun getMatchThreshold(): Float = _matchThreshold.floatValue

    /**
     * Get current top K value
     */
    fun getTopK(): Int = _topK.intValue
}

package me.grey.picquery.domain

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.R
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.model.Album
import timber.log.Timber

/**
 * Search Orchestrator - Coordinates search operations
 *
 * Responsibilities:
 * - Handle text search with translation support
 * - Handle image search
 * - Coordinate between EmbeddingService and SearchConfigurationService
 * - Manage search lock to prevent concurrent searches
 * - Handle translation errors gracefully
 */
class SearchOrchestrator(
    private val embeddingService: EmbeddingService,
    private val configurationService: SearchConfigurationService,
    private val objectBoxEmbeddingRepository: ObjectBoxEmbeddingRepository,
    private val translator: MLKitTranslator,
    private val dispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SearchOrchestrator"
    }

    private var searchingLock = false

    /**
     * Search by text with translation support
     *
     * @param text Search query text (will be translated if needed)
     * @param range Album range to search within
     * @param isSearchAll Whether to search all albums
     * @param onSuccess Callback with search results
     */
    suspend fun searchByText(
        text: String,
        range: List<Album>,
        isSearchAll: Boolean,
        onSuccess: suspend (MutableList<Pair<Long, Double>>) -> Unit
    ) {
        translateAndSearch(text, range, isSearchAll) { translatedText ->
            // Encode text to vector before searching
            val textVector = embeddingService.encodeText(translatedText)
            performVectorSearchV2(textVector, range, isSearchAll, onSuccess)
        }
    }

    /**
     * Search by image
     *
     * @param bitmap Image to search with
     * @param range Album range to search within
     * @param isSearchAll Whether to search all albums
     * @param onSuccess Callback with search results
     */
    suspend fun searchByImage(
        bitmap: Bitmap,
        range: List<Album>,
        isSearchAll: Boolean,
        onSuccess: suspend (MutableList<Pair<Long, Double>>) -> Unit
    ) {
        withContext(dispatcher) {
            if (searchingLock) {
                Timber.tag(TAG).w("Search already in progress")
                return@withContext
            }
            searchingLock = true

            try {
                val imageFeatures = embeddingService.encodeBitmap(bitmap)
                performVectorSearchV2(imageFeatures, range, isSearchAll, onSuccess)
            } finally {
                searchingLock = false
            }
        }
    }

    /**
     * Translate and search with fallback on error
     */
    private suspend fun translateAndSearch(
        text: String,
        range: List<Album>,
        isSearchAll: Boolean,
        searchFunction: suspend (String) -> Unit
    ) {
        translator.translate(
            text,
            onSuccess = { translatedText ->
                scope.launch {
                    searchFunction(translatedText)
                }
            },
            onError = { error ->
                scope.launch {
                    // Fallback to original text on translation error
                    searchFunction(text)
                    handleTranslationError(error)
                }
            }
        )
    }

    /**
     * Handle translation errors with logging and user notification
     */
    private fun handleTranslationError(error: Throwable) {
        Timber.tag("MLTranslator").e(
            context.getString(R.string.translation_error_log, error.message)
        )
        showToast(context.getString(R.string.translation_error_toast))
    }

    /**
     * Perform vector search V2 using ObjectBox
     */
    private suspend fun performVectorSearchV2(
        queryVector: FloatArray,
        range: List<Album>,
        isSearchAll: Boolean,
        onSuccess: suspend (MutableList<Pair<Long, Double>>) -> Unit
    ) = withContext(dispatcher) {
        try {
            searchingLock = true

            Timber.tag(TAG).d("Starting vector search V2")

            val albumIds = if (range.isEmpty() || isSearchAll) {
                Timber.tag(TAG).d("Search from all albums")
                null
            } else {
                Timber.tag(TAG).d("Search from: [${range.joinToString { it.label }}]")
                range.map { it.id }
            }

            val searchResults = objectBoxEmbeddingRepository.searchNearestVectors(
                queryVector = queryVector,
                topK = configurationService.getTopK(),
                similarityThreshold = configurationService.getMatchThreshold(),
                albumIds = albumIds
            )

            Timber.tag(TAG).d("Search completed: found ${searchResults.size} results")

            val results = searchResults.map { it.get().photoId to it.score }.toMutableList()
            onSuccess(results)
        } finally {
            searchingLock = false
        }
    }
}

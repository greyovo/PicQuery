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
        translateAndSearch(text) { translatedText ->
            val candidates = ChineseQueryExpander.mergeCandidates(text, translatedText)
            Timber.tag(TAG).i("Text search candidates for '$text': ${candidates.joinToString()}")
            val results = performTextCandidateSearch(candidates, range, isSearchAll)
            onSuccess(results)
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
                val results = performVectorSearchResults(imageFeatures, range, isSearchAll)
                onSuccess(results)
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
    private suspend fun performTextCandidateSearch(
        candidates: List<String>,
        range: List<Album>,
        isSearchAll: Boolean
    ): MutableList<Pair<Long, Double>> = withContext(dispatcher) {
        if (searchingLock) {
            Timber.tag(TAG).w("Search already in progress")
            return@withContext mutableListOf()
        }

        searchingLock = true
        try {
            val mergedScores = linkedMapOf<Long, Double>()

            candidates.forEach { candidate ->
                val textVector = embeddingService.encodeText(candidate)
                val candidateResults = performVectorSearchResults(textVector, range, isSearchAll)
                Timber.tag(TAG).d("Candidate '$candidate' returned ${candidateResults.size} result(s)")

                candidateResults.forEach { (photoId, score) ->
                    val previousScore = mergedScores[photoId]
                    if (previousScore == null || score < previousScore) {
                        mergedScores[photoId] = score
                    }
                }
            }

            mergedScores.entries
                .sortedBy { it.value }
                .map { it.key to it.value }
                .toMutableList()
        } finally {
            searchingLock = false
        }
    }

    private suspend fun performVectorSearchResults(
        queryVector: FloatArray,
        range: List<Album>,
        isSearchAll: Boolean
    ): MutableList<Pair<Long, Double>> = withContext(dispatcher) {
        try {
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

            searchResults.map { it.get().photoId to it.score }.toMutableList()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Vector search failed")
            mutableListOf()
        }
    }
}

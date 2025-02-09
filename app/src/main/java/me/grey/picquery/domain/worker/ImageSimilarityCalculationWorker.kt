package me.grey.picquery.domain.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker

import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.grey.picquery.common.calculateSimilarity
import me.grey.picquery.data.AppDatabase
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.model.ImageSimilarity
import me.grey.picquery.data.model.toFloatArray
import me.grey.picquery.domain.ImageSearcher
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Worker responsible for calculating image similarities.
 */
class ImageSimilarityCalculationWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val database: AppDatabase by inject()
    private val embeddingRepository by inject<EmbeddingRepository>()
    private val imageSearcher by inject<ImageSearcher>()
    private val imageSimilarityDao by lazy { database.imageSimilarityDao() }
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result = withContext(dispatcher) {
        try {
            Timber.tag("ImageSimilarity").d("Calculating similarities")
            
            val baseline = imageSearcher.getBaseLine()
            val calculatedSet = HashSet<Long>()
            var offset = 0
            val pageSize = 1000


            while (true) {
                val existingSimilarities = imageSimilarityDao.getSimilaritiesPaginated(pageSize, offset)
                if (existingSimilarities.isEmpty()) break

                calculatedSet.addAll(existingSimilarities.map { it.photoId })
                offset += pageSize
            }

            embeddingRepository.getAllEmbeddingsPaginated(pageSize)
                .flatMapMerge { embeddingsPage ->
                    flow {
                        val similarities = embeddingsPage
                            .filterNot { calculatedSet.contains(it.photoId) }
                            .map { comparedEmbedding ->
                                val similarityScore = calculateSimilarity(
                                    baseline,
                                    comparedEmbedding.data.toFloatArray()
                                )

                                Timber.tag("ImageSimilarity")
                                    .d("Similarity between ${comparedEmbedding.photoId} is $similarityScore")

                                ImageSimilarity(
                                    photoId = comparedEmbedding.photoId,
                                    similarityScore = similarityScore.toFloat()
                                )
                            }

                        emit(similarities)
                    }
                }
                .collect { similarities ->
                    if (similarities.isNotEmpty()) {
                        imageSimilarityDao.insertAll(similarities)
                    }
                }

            Result.success()
        } catch (e: Exception) {
            Log.e("ImageSimilarity", "Error calculating similarities", e)
            Result.failure()
        }
    }
}

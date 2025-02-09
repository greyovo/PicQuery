package me.grey.picquery.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.grey.picquery.data.model.ImageSimilarity

@Dao
interface ImageSimilarityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(similarity: ImageSimilarity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(similarities: List<ImageSimilarity>)

    @Query("SELECT * FROM image_similarity WHERE photo_id = :photoId")
    suspend fun getSimilaritiesForBasePhoto(photoId: Long): List<ImageSimilarity>

    suspend fun getAllSimilarities(): List<ImageSimilarity> {
        return getSimilaritiesInRange(0f, 1f)
    }

    @Query("DELETE FROM image_similarity WHERE photo_id = :photoId")
    suspend fun deleteByBasePhotoId(photoId: Long)

    // 添加一个 查询，查找相似度 在某个范围内的数据
    @Query("SELECT * FROM image_similarity WHERE similarity_score > :minSimilarityScore AND similarity_score < :maxSimilarityScore")
    suspend fun getSimilaritiesInRange(minSimilarityScore: Float, maxSimilarityScore: Float): List<ImageSimilarity>

    @Query("SELECT * FROM image_similarity LIMIT :pageSize OFFSET :offset")
    suspend fun getSimilaritiesPaginated(pageSize: Int, offset: Int): List<ImageSimilarity>

    fun getAllSimilaritiesFlow(pageSize: Int = 1000): Flow<List<ImageSimilarity>> = flow {
        var offset = 0
        while (true) {
            val similarities = getSimilaritiesPaginated(pageSize, offset)
            if (similarities.isEmpty()) break
            emit(similarities)
            offset += pageSize
        }
    }
}
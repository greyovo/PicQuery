package me.grey.picquery.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_similarity")
data class ImageSimilarity(

    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "photo_id")
    val photoId: Long,

    @ColumnInfo(name = "similarity_score")
    val similarityScore: Float
)

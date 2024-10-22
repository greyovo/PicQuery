package me.grey.picquery.data

import androidx.room.Database
import androidx.room.RoomDatabase
import me.grey.picquery.data.dao.AlbumDao
import me.grey.picquery.data.dao.EmbeddingDao
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Embedding

@Database(entities = [Embedding::class, Album::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun embeddingDao(): EmbeddingDao

    abstract fun albumDao(): AlbumDao
}


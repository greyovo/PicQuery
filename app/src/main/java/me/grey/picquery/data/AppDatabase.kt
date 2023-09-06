package me.grey.picquery.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import me.grey.picquery.PicQueryApplication
import me.grey.picquery.data.dao.EmbeddingDao
import me.grey.picquery.data.model.Embedding

@Database(entities = [Embedding::class], version = 1)
abstract class AppDatabase private constructor() : RoomDatabase() {
    companion object {
        @Volatile
        private var database: AppDatabase? = null
        
        val instance: AppDatabase
            get() {
                if (database == null) {
                    database = Room.databaseBuilder(
                        PicQueryApplication.context,
                        AppDatabase::class.java, "app-db"
                    ).build()
                }
                return database as AppDatabase
            }
    }

    abstract fun embeddingDao(): EmbeddingDao
}


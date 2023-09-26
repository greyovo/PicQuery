package me.grey.picquery.data

import androidx.room.Database
import androidx.room.RoomDatabase
import me.grey.picquery.data.dao.AlbumDao
import me.grey.picquery.data.dao.EmbeddingDao
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Embedding

@Database(entities = [Embedding::class, Album::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
//    companion object {
//        @Volatile
//        private var database: AppDatabase? = null
//
//        val instance: AppDatabase
//            get() {
//                if (database == null) {
//                    database = Room.databaseBuilder(
//                        PicQueryApplication.context,
//                        AppDatabase::class.java, "app-db"
//                    ).build()
//                }
//                return database as AppDatabase
//            }
//    }

    abstract fun embeddingDao(): EmbeddingDao

    abstract fun albumDao(): AlbumDao
}


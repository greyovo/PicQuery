package me.grey.picquery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.grey.picquery.data.dao.AlbumDao
import me.grey.picquery.data.dao.EmbeddingDao
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Embedding

@Database(entities = [Embedding::class, Album::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun embeddingDao(): EmbeddingDao

    abstract fun albumDao(): AlbumDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app-db")
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Define the migration from version 2 to version 3
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Perform necessary schema changes here
                // For example, adding a new column:
                // database.execSQL("ALTER TABLE table_name ADD COLUMN new_column_name INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}


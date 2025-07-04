package me.grey.picquery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.grey.picquery.data.dao.AlbumDao
import me.grey.picquery.data.dao.EmbeddingDao
import me.grey.picquery.data.dao.ImageSimilarityDao
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.ImageSimilarity

@Database(
    entities = [Embedding::class, Album::class, ImageSimilarity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun embeddingDao(): EmbeddingDao

    abstract fun albumDao(): AlbumDao

    abstract fun imageSimilarityDao(): ImageSimilarityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app-db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Define the migration from version 2 to version 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Perform necessary schema changes here
                // For example, adding a new column:
                // database.execSQL("ALTER TABLE table_name ADD COLUMN new_column_name INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS image_similarity (\n" +
                        "    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,\n" +
                        "    base_photo_id INTEGER NOT NULL,\n" +
                        "    compared_photo_id INTEGER NOT NULL,\n" +
                        "    similarity_score REAL NOT NULL\n" +
                        ");"
                )
            }
        }
    }
}

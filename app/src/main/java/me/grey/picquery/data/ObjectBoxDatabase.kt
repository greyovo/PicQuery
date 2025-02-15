package me.grey.picquery.data

import android.content.Context
import io.objectbox.BoxStore
import me.grey.picquery.data.dao.ObjectBoxEmbeddingDao
import me.grey.picquery.data.model.MyObjectBox
import me.grey.picquery.data.model.ObjectBoxEmbedding

class ObjectBoxDatabase private constructor() {
    private lateinit var boxStore: BoxStore

    fun initialize(context: Context) {
        boxStore = MyObjectBox.builder()
        .androidContext(context.applicationContext)
            .build()
    }

    fun embeddingDao(): ObjectBoxEmbeddingDao {
        return ObjectBoxEmbeddingDao(boxStore.boxFor(ObjectBoxEmbedding::class.java))
    }

    companion object {
        @Volatile
        private var INSTANCE: ObjectBoxDatabase? = null

        fun getDatabase(): ObjectBoxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = ObjectBoxDatabase()
                INSTANCE = instance
                instance
            }
        }
    }

    fun close() {
        boxStore.close()
    }
}
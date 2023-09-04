package me.grey.picquery.data

import android.util.Log
import me.grey.picquery.data.model.Embedding
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class ImageEmbeddingRepository(
    private val savedDirectory: File
) {
    companion object {
        private const val TAG = "ImageEmbeddingRepository"
        private const val EMBEDDING_FILE = "img-feat.bin" // 存储为二进制数据
    }

    val embeddingMap = mutableMapOf<Long, Embedding>() // <id, feature>

    fun update(emb: Embedding) {
        embeddingMap[emb.id] = emb
    }

    fun updateAll(list: List<Embedding>) {
        list.forEach {
            embeddingMap[it.id] = it
        }
    }

    // 序列化到文件中
    private fun saveToFile() {
        if (embeddingMap.isEmpty()) return
        try {
            val file = File(savedDirectory, EMBEDDING_FILE)
            if (!file.exists()) {
                file.createNewFile()
            }
            val fileOutputStream = file.outputStream()
            val objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(embeddingMap)
            objectOutputStream.close()
            fileOutputStream.close()
        } catch (e: IOException) {
            Log.e(TAG, "序列化索引文件失败")
        }

    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFromFile() {
        val start = System.currentTimeMillis()
        try {
            val file = File(savedDirectory, EMBEDDING_FILE)
            val fileInputStream = file.inputStream()
            val objectInputStream = ObjectInputStream(fileInputStream)

            // 读取Embedding集合，反序列化
            val objects = objectInputStream.readObject() as Map<Long, Embedding>

            // 关闭资源
            objectInputStream.close()
            fileInputStream.close()
            Log.d(TAG, "Embeddings size: ${embeddingMap.size}")
            Log.d(TAG, "loadEmbeddings cost ${System.currentTimeMillis() - start}ms")
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            Log.e(TAG, e.toString())
        }
    }
}
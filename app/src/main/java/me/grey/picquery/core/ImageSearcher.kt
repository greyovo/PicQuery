package me.grey.picquery.core

import android.graphics.Bitmap
import android.util.Log
import me.grey.picquery.core.encoder.ImageEncoder
import me.grey.picquery.core.encoder.TextEncoder
import me.grey.picquery.data.Embedding
import java.io.*
import java.nio.FloatBuffer
import java.util.*

class ImageSearcher(
    private val imageEncoder: ImageEncoder,
    private val textEncoder: TextEncoder,
    private val savedDirectory: File
) {

    val embeddingList = mutableListOf<Embedding>()

    companion object {
        const val EMBEDDING_FILE = "img-feat.bin" // 存储为二进制数据
        const val TAG = "ImageSearcher"
    }

    init {

    }

    fun encodeBatch(imageBitmaps: List<Bitmap>) {
        val batchResult = mutableListOf<Embedding>()
        for (bitmap in imageBitmaps) {
            val feat: FloatBuffer = imageEncoder.encode(bitmap)
            batchResult.add(Embedding(id = "1", data = feat.array()))
        }
        saveEmbeddings(batchResult)
    }

    // 序列化到文件中
    private fun saveEmbeddings(embeddings: List<Embedding>) {
//        val savedFile = File("$savedPath/${EMBEDDING_FILE}")
//        val sb = StringBuilder()
//        for (emb in embeddings) {
//            sb.append("${emb.id}#${emb.data.contentToString()}\n")
//        }

        try {
            val file = File(savedDirectory, EMBEDDING_FILE)
            if (!file.exists()) {
                file.createNewFile()
            }
            val fileOutputStream = file.outputStream()
            val objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(embeddings)
            objectOutputStream.close()
            fileOutputStream.close()
        } catch (e: IOException) {
            Log.e(TAG, "序列化索引文件失败")
        }

    }

    @Suppress("UNCHECKED_CAST")
    private fun loadEmbeddings() {
        val start = System.currentTimeMillis()
        try {
            val file = File(savedDirectory, EMBEDDING_FILE)
            val fileInputStream = file.inputStream()
            val objectInputStream = ObjectInputStream(fileInputStream)

            // 读取对象集合
            val objects = objectInputStream.readObject() as List<Embedding>

            // 关闭资源
            objectInputStream.close()
            fileInputStream.close()
            Log.d(TAG, "loadEmbeddings: ${objects[13].id}, ${objects[13].data}")
            Log.d(TAG, "loadEmbeddings cost ${System.currentTimeMillis() - start}ms")
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            Log.e(TAG, e.toString())
        }

    }

    fun search() {
        loadEmbeddings()
    }

    private fun searchByEmbedding() {

    }
}
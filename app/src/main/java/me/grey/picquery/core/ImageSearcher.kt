package me.grey.picquery.core

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import me.grey.picquery.core.encoder.ImageEncoder
import me.grey.picquery.core.encoder.TextEncoder
import me.grey.picquery.data.model.Embedding
import me.grey.picquery.data.model.Photo
import me.grey.picquery.util.decodeSampledBitmapFromFile
import me.grey.picquery.util.decodeSampledBitmapFromFileDescriptor
import me.grey.picquery.util.loadThumbnail
import me.grey.picquery.util.saveBitMap
import java.io.*
import java.nio.FloatBuffer
import java.util.*

class ImageSearcher(
    private val imageEncoder: ImageEncoder,
    private val textEncoder: TextEncoder,
    private val savedDirectory: File
) {

    private val embeddingList = mutableListOf<Embedding>()

    companion object {
        private const val EMBEDDING_FILE = "img-feat.bin" // 存储为二进制数据
        private const val TAG = "ImageSearcher"
    }

    init {

    }


    fun encodePhotoList(contentResolver: ContentResolver, photos: List<Photo>, context: Context?) {
        for (photo in photos) {
//            ThumbnailUtils.createImageThumbnail(File(Uri), Size(224, 224), null)

            val thumbnailBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                null
                val start = System.currentTimeMillis()
                val res = contentResolver.loadThumbnail(photo.uri, Size(224, 224), null)
                Log.d(TAG, "input cost: ${System.currentTimeMillis() - start}ms")
                res
            } else {
                val inp = contentResolver.openFileDescriptor(photo.uri, "r")
                val thumbnailBitmap =
                    decodeSampledBitmapFromFileDescriptor(inp!!.fileDescriptor, 224, 224)
                inp.close()
                thumbnailBitmap
            }
//            thumbnailBitmap?.let { encode(it) }
            context?.let { saveBitMap(it, thumbnailBitmap, photo.label) }
            encode(thumbnailBitmap)
        }
    }

    private fun encode(bitmap: Bitmap) {
        val feat: FloatBuffer = imageEncoder.encode(bitmap)
        embeddingList.add(Embedding(id = "1", data = feat.array()))
        saveEmbeddings()
    }

    fun encodeBatch(imageBitmaps: List<Bitmap>) {
        val batchResult = mutableListOf<Embedding>()
        for (bitmap in imageBitmaps) {
            val feat: FloatBuffer = imageEncoder.encode(bitmap)
            batchResult.add(Embedding(id = "1", data = feat.array()))
        }
        embeddingList.addAll(batchResult)
        saveEmbeddings()
    }

    // 序列化到文件中
    private fun saveEmbeddings() {
//        val savedFile = File("$savedPath/${EMBEDDING_FILE}")
//        val sb = StringBuilder()
//        for (emb in embeddings) {
//            sb.append("${emb.id}#${emb.data.contentToString()}\n")
//        }

        if (embeddingList.isEmpty()) return

        try {
            val file = File(savedDirectory, EMBEDDING_FILE)
            if (!file.exists()) {
                file.createNewFile()
            }
            val fileOutputStream = file.outputStream()
            val objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeObject(embeddingList)
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

            // 读取Embedding集合，反序列化
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
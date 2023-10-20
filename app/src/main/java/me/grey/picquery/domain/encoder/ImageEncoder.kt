package me.grey.picquery.domain.encoder

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.common.AssetUtil
import me.grey.picquery.common.MemoryFormat
import me.grey.picquery.common.allocateFloatBuffer
import me.grey.picquery.common.bitmapToFloatBuffer
import java.nio.FloatBuffer
import java.util.Collections

private val normMeanRGB = floatArrayOf(0.48145467f, 0.4578275f, 0.40821072f)
private val normStdRGB = floatArrayOf(0.26862955f, 0.2613026f, 0.2757771f)

val IMAGE_INPUT_SIZE = Size(224, 224)

class ImageEncoder {
    companion object {
        private const val modelPath = "clip-image-int8.ort"
        private const val floatBufferElementCount = 3 * 224 * 224
    }

    private var ortSession: OrtSession? = null

    init {
        val ortEnv = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        options.addConfigEntry("session.load_model_format", "ORT")
        ortSession = ortEnv?.createSession(AssetUtil.assetFilePath(context, modelPath), options)
    }

    /**
     * 缩放为短边为224像素；
     * 长的缩短，短的拉长。
     */
    private fun resize(bitmap: Bitmap): Bitmap {
        return if (bitmap.width < bitmap.height) {
            val longHeight = bitmap.height * 224 / bitmap.width
            Bitmap.createScaledBitmap(bitmap, 224, longHeight, false)
        } else {
            val longWidth = bitmap.width * 224 / bitmap.height
            Bitmap.createScaledBitmap(bitmap, longWidth, 224, false)
        }
    }

    private fun centerCrop(bitmap: Bitmap): Bitmap {
        // 计算裁切位置
        val x: Int = (bitmap.width - 224) / 2
        val y: Int = (bitmap.height - 224) / 2
        return Bitmap.createBitmap(bitmap, x, y, 224, 224)
    }


//    private fun toRGB(bitmap: Bitmap): Bitmap {
//        val width = bitmap.width
//        val height = bitmap.height
//        val pixels = IntArray(width * height)
//        bitmap.getPixels(pixels, 0, width, 0, 0, width, height) // 获取每个像素的颜色值
//
//        for (i in pixels.indices) { // 将每个像素的颜色值转换为RGB格式
//            val color = pixels[i]
//            val red: Int = Color.red(color)
//            val green: Int = Color.green(color)
//            val blue: Int = Color.blue(color)
//            val rgb = red shl 16 or (green shl 8) or blue
//            pixels[i] = rgb
//        }
//        return bitmap
//    }

    private fun preprocess(bitmap: Bitmap): Bitmap {
        val start = System.currentTimeMillis()
        if (bitmap.width == 224 && bitmap.height == 224) {
//            Log.d("preprocess", "w=h=224, no preprocess.")
            return bitmap
        }
        val res = centerCrop(resize(bitmap))
//        Log.d("preprocess", "${System.currentTimeMillis() - start} ms")
        return res
    }


    suspend fun encode(bitmap: Bitmap) = withContext<FloatBuffer>(Dispatchers.Default) {
        val imageBitmap = preprocess(bitmap)
        val floatBuffer = allocateFloatBuffer(floatBufferElementCount)
        floatBuffer.rewind()
        bitmapToFloatBuffer(
            imageBitmap,
            0, 0,
            224, 224,
            normMeanRGB,
            normStdRGB,
            floatBuffer,
            0,
            MemoryFormat.CONTIGUOUS,
        )
        floatBuffer.rewind()

        val inputName = ortSession?.inputNames?.iterator()?.next()
        val shape: LongArray = longArrayOf(1, 3, 224, 224)
        val env = OrtEnvironment.getEnvironment()
        env.use {
            val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)
            val output: OrtSession.Result? =
                ortSession?.run(Collections.singletonMap(inputName, tensor))
            val resultBuffer = output?.get(0) as OnnxTensor
            return@withContext (resultBuffer.floatBuffer)
        }
    }
}
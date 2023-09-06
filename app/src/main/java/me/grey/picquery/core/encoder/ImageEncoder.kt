package me.grey.picquery.core.encoder

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.common.allocateFloatBuffer
import me.grey.picquery.common.assetFilePath
import me.grey.picquery.common.bitmapToFloatBuffer
import org.pytorch.*
import org.pytorch.torchvision.TensorImageUtils
import java.nio.FloatBuffer
import java.util.*

private val normMeanRGB = floatArrayOf(0.48145467f, 0.4578275f, 0.40821072f)
private val normStdRGB = floatArrayOf(0.26862955f, 0.2613026f, 0.2757771f)

val IMAGE_INPUT_SIZE = Size(224, 224)

object ImageEncoder {
    private const val modelPath = "clip-image-encoder-quant-int8.onnx"

    private var ortSession: OrtSession? = null

    init {
        val ortEnv = OrtEnvironment.getEnvironment()
        ortSession = ortEnv?.createSession(assetFilePath(context, modelPath))
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

    fun encode(bitmap: Bitmap): FloatBuffer {
        val imgData = preprocess(bitmap)
        val start = System.currentTimeMillis()
        val floatBuffer = allocateFloatBuffer(3 * 224 * 224)
//        TensorImageUtils.bitmapToFloatBuffer(
        bitmapToFloatBuffer(
            imgData,
            0, 0,
            224, 224,
            normMeanRGB,
            normStdRGB,
            floatBuffer,
            0,
            MemoryFormat.CHANNELS_LAST,
        )
//        Log.d("bitmapToBuffer", "${System.currentTimeMillis() - start} ms")

//        val imgDataShort = floatBufferToFloat16Buffer(imgData)
//        Log.d("ONNX imgData size", imgData.limit().toString())
//        Log.d("ONNX imgDataShort size", imgDataShort.limit().toString())
        val inputName = ortSession?.inputNames?.iterator()?.next()
        val shape: LongArray = longArrayOf(1, 3, 224, 224)
        val env = OrtEnvironment.getEnvironment()
        env.use {
            val tensor = OnnxTensor.createTensor(env, floatBuffer, shape)
            tensor.use {
                val start2 = System.currentTimeMillis()
                val output: OrtSession.Result? =
                    ortSession?.run(Collections.singletonMap(inputName, tensor))
//                Log.d("ONNX cost", "${System.currentTimeMillis() - start2} ms")
                output.use {
                    val resultBuffer = output?.get(0) as OnnxTensor
                    return (resultBuffer.floatBuffer)
                }
            }
        }
    }


}
package me.grey.picquery.feature.mobileclip2

import android.graphics.Bitmap
import me.grey.picquery.feature.base.Preprocessor
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class PreprocessorMobileCLIPv2 : Preprocessor {
    private val imageProcessor: ImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR)).build()

    companion object {
        const val IMAGE_SIZE = 256
    }

    override suspend fun preprocessBatch(input: List<Bitmap>): List<TensorBuffer> {
        val arr = mutableListOf<TensorBuffer>()
        for (bitmap in input) {
            arr.add(preprocess(bitmap))
        }
        return arr
    }

    override suspend fun preprocess(input: Bitmap): TensorBuffer {
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(input)

        // Preprocess the image and convert it into a TensorImage
        tensorImage = imageProcessor.process(tensorImage)

        val tensor = TensorBuffer.createFixedSize(
            intArrayOf(1, 3, IMAGE_SIZE, IMAGE_SIZE),
            DataType.FLOAT32
        )
        tensor.loadBuffer(tensorImage.buffer)
        return tensor
    }
}

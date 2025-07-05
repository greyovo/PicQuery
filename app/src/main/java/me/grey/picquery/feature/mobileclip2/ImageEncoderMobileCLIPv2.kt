package me.grey.picquery.feature.mobileclip2

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.FileNotFoundException
import java.nio.FloatBuffer
import kotlin.system.measureTimeMillis
import me.grey.picquery.common.AssetUtil
import me.grey.picquery.feature.base.ImageEncoder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import timber.log.Timber

class ImageEncoderMobileCLIPv2(context: Context, private val preprocessor: PreprocessorMobileCLIPv2) :
    ImageEncoder {
    private val interpreter: Interpreter

    companion object {
        private const val TAG = "ImageEncoderLiteRT"
        private const val IMAGE_SIZE = 256
        private const val MODEL_PATH = "mobileclip-image.tflite"
    }

    init {

        val modelFile = AssetUtil.assetFile(context, MODEL_PATH)
            ?: throw FileNotFoundException("Model: $MODEL_PATH not exist.")

        // Initialize interpreter with GPU delegate
        val options = Interpreter.Options()
        val compatList = CompatibilityList()

        if (compatList.isDelegateSupportedOnThisDevice) {
            // if the device has a supported GPU, add the GPU delegate
            val delegateOptions = compatList.bestOptionsForThisDevice
            delegateOptions?.forceBackend = GpuDelegateFactory.Options.GpuBackend.OPENCL
            options.addDelegate(GpuDelegate(delegateOptions))
            Timber.tag(TAG).d("Supported GPU, add the GPU delegate")
        } else {
            // if the GPU is not supported, run on 4 threads
            Timber.tag(TAG).d("GPU is not supported, run on 4 threads on CPU")
            options.setNumThreads(4)
        }
        interpreter = Interpreter(modelFile, options)
    }

    override suspend fun encodeBatch(bitmaps: List<Bitmap>): List<FloatArray> {
        val shape = interpreter.getInputTensor(0)?.shape()
        Log.d(TAG, "Input shape: ${shape?.joinToString()}")
        val outputArray = arrayListOf<FloatArray>()
        for (bitmap in bitmaps) {
            // Preprocess the image and convert it into a TensorImage for classification.
            val tensorImage = preprocessor.preprocess(bitmap)
            val tensor = TensorBuffer.createFixedSize(
                intArrayOf(1, 3, IMAGE_SIZE, IMAGE_SIZE),
                DataType.FLOAT32
            )
            tensor.loadBuffer(tensorImage.buffer)
            outputArray.add(encodeWithTFLite(tensor))
        }
        return outputArray
    }

    private fun encodeWithTFLite(tensorImage: TensorBuffer): FloatArray {
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputBuffer = FloatBuffer.allocate(outputShape[1])

        outputBuffer.rewind()
        val output = FloatArray(outputBuffer.capacity())
        val time = measureTimeMillis {
            interpreter.run(tensorImage.buffer, outputBuffer)
            outputBuffer.rewind()
            outputBuffer[output]
        }

        Log.d(TAG, "LiteRT encode 1 pic cost: $time ms")

        return output
    }
}

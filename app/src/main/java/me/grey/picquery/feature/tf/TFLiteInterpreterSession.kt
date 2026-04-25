package me.grey.picquery.feature.tf

import android.content.Context
import java.io.Closeable
import java.io.FileNotFoundException
import me.grey.picquery.common.AssetUtil
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import timber.log.Timber

class TFLiteInterpreterSession private constructor(
    val interpreter: Interpreter,
    private val gpuDelegate: GpuDelegate?
) : Closeable {
    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }

    companion object {
        private const val TAG = "TFLiteInterpreterSession"

        fun fromAsset(
            context: Context,
            modelPath: String,
            useGpuDelegate: Boolean = true,
            numThreads: Int = 4
        ): TFLiteInterpreterSession {
            val modelFile = AssetUtil.assetFile(context, modelPath)
                ?: throw FileNotFoundException("Model: $modelPath not exist.")
            val options = Interpreter.Options()
            val gpuDelegate = configureDelegate(options, useGpuDelegate, numThreads)
            return TFLiteInterpreterSession(Interpreter(modelFile, options), gpuDelegate)
        }

        private fun configureDelegate(
            options: Interpreter.Options,
            useGpuDelegate: Boolean,
            numThreads: Int
        ): GpuDelegate? {
            if (!useGpuDelegate) {
                options.setNumThreads(numThreads)
                return null
            }

            val compatList = CompatibilityList()
            if (!compatList.isDelegateSupportedOnThisDevice) {
                Timber.tag(TAG).d("GPU is not supported, run on $numThreads threads on CPU")
                options.setNumThreads(numThreads)
                return null
            }

            val delegateOptions = compatList.bestOptionsForThisDevice
                ?: GpuDelegateFactory.Options()
            delegateOptions.forceBackend = GpuDelegateFactory.Options.GpuBackend.OPENCL
            val delegate = GpuDelegate(delegateOptions)
            options.addDelegate(delegate)
            Timber.tag(TAG).d("Supported GPU, add the GPU delegate")
            return delegate
        }
    }
}

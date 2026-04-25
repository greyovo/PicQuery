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
            runtimeConfig: TFLiteRuntimeConfig = TFLiteRuntimeConfig.Default
        ): TFLiteInterpreterSession {
            val modelFile = AssetUtil.assetFile(context, modelPath)
                ?: throw FileNotFoundException("Model: $modelPath not exist.")
            val options = Interpreter.Options()
            val gpuDelegate = configureDelegate(options, runtimeConfig)
            return TFLiteInterpreterSession(Interpreter(modelFile, options), gpuDelegate)
        }

        private fun configureDelegate(
            options: Interpreter.Options,
            runtimeConfig: TFLiteRuntimeConfig
        ): GpuDelegate? {
            options.setNumThreads(runtimeConfig.numThreads)

            if (!runtimeConfig.useGpuDelegate) {
                Timber.tag(TAG).d(
                    "Run TFLite on CPU with ${runtimeConfig.numThreads} threads"
                )
                return null
            }

            val compatList = CompatibilityList()
            if (!compatList.isDelegateSupportedOnThisDevice) {
                Timber.tag(TAG).d(
                    "GPU is not supported, run on ${runtimeConfig.numThreads} threads on CPU"
                )
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

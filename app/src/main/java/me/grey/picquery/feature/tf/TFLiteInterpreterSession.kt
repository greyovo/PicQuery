package me.grey.picquery.feature.tf

import android.content.Context
import java.io.Closeable
import java.io.FileNotFoundException
import me.grey.picquery.common.AssetUtil
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.gpu.GpuDelegateFactory
import timber.log.Timber

class TFLiteInterpreterSession private constructor(
    val interpreter: Interpreter,
    private val delegate: Delegate?
) : Closeable {
    val isNativeXnnpackThreadPoolActive: Boolean =
        (delegate as? NativeXnnpackDelegate)?.isThreadPoolActive == true
    private var closed = false

    override fun close() {
        synchronized(interpreter) {
            if (closed) return
            closed = true
            try {
                interpreter.close()
            } finally {
                delegate?.close()
            }
        }
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
            val delegate = configureDelegate(options, runtimeConfig)
            var interpreter: Interpreter? = null
            try {
                val createdInterpreter = Interpreter(modelFile, options)
                interpreter = createdInterpreter
                return TFLiteInterpreterSession(createdInterpreter, delegate)
            } catch (failure: Throwable) {
                try {
                    interpreter?.close()
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                try {
                    delegate?.close()
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        }

        private fun configureDelegate(
            options: Interpreter.Options,
            runtimeConfig: TFLiteRuntimeConfig
        ): Delegate? {
            options.setNumThreads(runtimeConfig.numThreads)

            if (runtimeConfig.useNativeXnnpack) {
                options.setUseXNNPACK(false)
                Timber.tag(TAG).d("Run native XNNPACK with ${runtimeConfig.numThreads} threads")
                return attachDelegate(options, NativeXnnpackDelegate(runtimeConfig.numThreads))
            }

            if (!runtimeConfig.useGpuDelegate) {
                Timber.tag(TAG).d(
                    "Run TFLite on CPU with ${runtimeConfig.numThreads} threads"
                )
                return null
            }

            val compatList = CompatibilityList()
            return if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                    ?: GpuDelegateFactory.Options()
                delegateOptions.forceBackend = GpuDelegateFactory.Options.GpuBackend.OPENCL
                Timber.tag(TAG).d("Supported GPU, add the GPU delegate")
                attachDelegate(options, GpuDelegate(delegateOptions))
            } else {
                Timber.tag(TAG).d(
                    "GPU is not supported, run on ${runtimeConfig.numThreads} threads on CPU"
                )
                null
            }
        }

        private fun attachDelegate(options: Interpreter.Options, delegate: Delegate): Delegate {
            try {
                options.addDelegate(delegate)
                return delegate
            } catch (failure: Throwable) {
                try {
                    delegate.close()
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
        }
    }
}

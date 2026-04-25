package me.grey.picquery.feature.tf

data class TFLiteRuntimeConfig(
    val useGpuDelegate: Boolean = false,
    val numThreads: Int = 4
) {
    init {
        require(numThreads > 0) { "TFLite thread count must be positive." }
    }

    companion object {
        const val DEFAULT_NUM_THREADS = 4
        val Default = TFLiteRuntimeConfig()
    }
}

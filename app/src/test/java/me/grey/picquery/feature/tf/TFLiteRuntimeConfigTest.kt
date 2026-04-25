package me.grey.picquery.feature.tf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TFLiteRuntimeConfigTest {
    @Test
    fun `defaults to CPU runtime with bounded thread count`() {
        val config = TFLiteRuntimeConfig.Default

        assertFalse(config.useGpuDelegate)
        assertEquals(4, config.numThreads)
    }

    @Test
    fun `allows opting into GPU delegate`() {
        val config = TFLiteRuntimeConfig(useGpuDelegate = true, numThreads = 2)

        assertTrue(config.useGpuDelegate)
        assertEquals(2, config.numThreads)
    }

    @Test
    fun `rejects non-positive thread counts`() {
        assertThrows(IllegalArgumentException::class.java) {
            TFLiteRuntimeConfig(numThreads = 0)
        }
    }
}

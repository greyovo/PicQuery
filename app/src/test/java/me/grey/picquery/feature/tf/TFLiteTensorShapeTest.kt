package me.grey.picquery.feature.tf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TFLiteTensorShapeTest {
    @Test
    fun `counts all output elements`() {
        assertEquals(512, TFLiteTensorShape.outputElementCount(intArrayOf(1, 512)))
        assertEquals(1024, TFLiteTensorShape.outputElementCount(intArrayOf(2, 512)))
        assertEquals(768, TFLiteTensorShape.outputElementCount(intArrayOf(1, 1, 768)))
    }

    @Test
    fun `rejects invalid output shapes`() {
        assertThrows(IllegalArgumentException::class.java) {
            TFLiteTensorShape.outputElementCount(intArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TFLiteTensorShape.outputElementCount(intArrayOf(1, 0, 512))
        }
    }
}

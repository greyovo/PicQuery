package me.grey.picquery.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class BPETokenizerTest {
    @Test
    fun utf8BytesAreTreatedAsUnsignedValues() {
        val bytes = utf8ByteValues("米")

        assertEquals(listOf(231, 177, 179), bytes.toList())
    }
}

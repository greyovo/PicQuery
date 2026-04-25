package me.grey.picquery.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertorTest {
    @Test
    fun remainingTimeIsNeverNegative() {
        assertEquals(0L, calculateRemainingTime(current = 1716, total = 616, costPerItem = 35))
    }
}

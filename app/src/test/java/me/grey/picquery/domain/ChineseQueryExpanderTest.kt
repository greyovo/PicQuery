package me.grey.picquery.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseQueryExpanderTest {
    @Test
    fun expandsChineseFoodAliasesBeforeTranslation() {
        val candidates = ChineseQueryExpander.mergeCandidates("米粉", "rice")

        assertEquals("rice noodles", candidates.first())
        assertTrue(candidates.contains("vermicelli"))
        assertTrue(candidates.contains("noodles"))
        assertTrue(candidates.contains("rice"))
        assertTrue(candidates.contains("米粉"))
    }

    @Test
    fun deduplicatesExpandedAndTranslatedCandidates() {
        val candidates = ChineseQueryExpander.mergeCandidates("米粉", "rice noodles")

        assertEquals(6, candidates.size)
        assertEquals("rice noodles", candidates.first())
    }
}

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

    @Test
    fun deduplicatesCaseVariantsAndKeepsFirstSpelling() {
        assertEquals(listOf("Dog"), ChineseQueryExpander.mergeCandidates("dog", "Dog"))
        assertEquals(listOf("Astronaut"), ChineseQueryExpander.mergeCandidates("ASTRONAUT", "Astronaut"))
    }

    @Test
    fun deduplicatesEquivalentWhitespaceWithoutRewritingFirstCandidate() {
        val candidates = ChineseQueryExpander.mergeCandidates(
            "a\tphoto\nof a dog",
            "  A   photo of a DOG  "
        )

        assertEquals(listOf("A   photo of a DOG"), candidates)
    }

    @Test
    fun preservesAliasPriorityWhenTranslationDiffersOnlyInCase() {
        assertEquals(listOf("dog", "狗"), ChineseQueryExpander.mergeCandidates("狗", "Dog"))
    }

    @Test
    fun preservesDistinctChineseAliasesAndTheirOrderWhenTranslationDuplicatesOne() {
        val candidates = ChineseQueryExpander.mergeCandidates("米粉", "RICE\tNOODLES")

        assertEquals(
            listOf("rice noodles", "rice noodle soup", "vermicelli", "rice vermicelli", "noodles", "米粉"),
            candidates
        )
    }

    @Test
    fun preservesDifferentMeaningsAndPunctuation() {
        assertEquals(listOf("dog", "puppy", "狗"), ChineseQueryExpander.mergeCandidates("狗", "puppy"))
        assertEquals(listOf("puppy", "dog"), ChineseQueryExpander.mergeCandidates("dog", "puppy"))
        assertEquals(listOf("Dog", "dog?"), ChineseQueryExpander.mergeCandidates("dog?", "Dog"))
    }

    @Test
    fun ignoresBlankCandidates() {
        assertEquals(emptyList<String>(), ChineseQueryExpander.mergeCandidates(" \t\n", "  \r"))
    }
}

package me.grey.picquery.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumManagerProgressTest {
    @Test
    fun cumulativeChunkCallbacksAreConvertedToGlobalProgress() {
        val firstPageStart = 0
        val secondPageStart = 500

        val reported = listOf(
            aggregateAlbumEncodingProgress(firstPageStart, 100, 616),
            aggregateAlbumEncodingProgress(firstPageStart, 200, 616),
            aggregateAlbumEncodingProgress(firstPageStart, 300, 616),
            aggregateAlbumEncodingProgress(firstPageStart, 400, 616),
            aggregateAlbumEncodingProgress(firstPageStart, 500, 616),
            aggregateAlbumEncodingProgress(secondPageStart, 100, 616),
            aggregateAlbumEncodingProgress(secondPageStart, 116, 616)
        )

        assertEquals(listOf(100, 200, 300, 400, 500, 600, 616), reported)
    }

    @Test
    fun progressNeverExceedsTotal() {
        assertEquals(616, aggregateAlbumEncodingProgress(500, 500, 616))
    }
}

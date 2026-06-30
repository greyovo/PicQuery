package me.grey.picquery.domain

import android.net.Uri
import io.mockk.mockk
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AlbumChangeDetector] 单元测试
 *
 * 测试变更检测的核心差分逻辑，包括纯 ID 差分和带 Photo 对象的完整检测。
 */
class AlbumChangeDetectorTest {

    private fun mockAlbum(id: Long = 1L, label: String = "Camera", count: Long = 10) = Album(
        id = id,
        label = label,
        coverPath = "/path/to/cover",
        timestamp = 1000L,
        count = count
    )

    private fun mockPhoto(id: Long, albumId: Long = 1L) = Photo(
        id = id,
        label = "photo_$id",
        uri = mockk<Uri>(relaxed = true),
        path = "/path/to/photo_$id",
        timestamp = 1000L + id,
        albumID = albumId,
        albumLabel = "Camera"
    )

    // ============ diffIds 纯逻辑测试 ============

    @Test
    fun `diffIds returns empty when no changes`() {
        val currentIds = setOf(1L, 2L, 3L)
        val indexedIds = setOf(1L, 2L, 3L)

        val diff = AlbumChangeDetector.diffIds(currentIds, indexedIds)

        assertTrue(diff.addedIds.isEmpty())
        assertTrue(diff.removedIds.isEmpty())
        assertFalse(diff.hasChanges)
    }

    @Test
    fun `diffIds detects added photos`() {
        val currentIds = setOf(1L, 2L, 3L, 4L, 5L)
        val indexedIds = setOf(1L, 2L, 3L)

        val diff = AlbumChangeDetector.diffIds(currentIds, indexedIds)

        assertEquals(setOf(4L, 5L), diff.addedIds)
        assertTrue(diff.removedIds.isEmpty())
        assertTrue(diff.hasChanges)
        assertEquals(2, diff.addedCount)
    }

    @Test
    fun `diffIds detects removed photos`() {
        val currentIds = setOf(1L, 2L)
        val indexedIds = setOf(1L, 2L, 3L, 4L)

        val diff = AlbumChangeDetector.diffIds(currentIds, indexedIds)

        assertTrue(diff.addedIds.isEmpty())
        assertEquals(setOf(3L, 4L), diff.removedIds)
        assertTrue(diff.hasChanges)
        assertEquals(2, diff.removedCount)
    }

    @Test
    fun `diffIds detects both added and removed photos`() {
        val currentIds = setOf(1L, 2L, 5L, 6L)
        val indexedIds = setOf(1L, 2L, 3L, 4L)

        val diff = AlbumChangeDetector.diffIds(currentIds, indexedIds)

        assertEquals(setOf(5L, 6L), diff.addedIds)
        assertEquals(setOf(3L, 4L), diff.removedIds)
        assertTrue(diff.hasChanges)
        assertEquals(4, diff.addedCount + diff.removedCount)
    }

    @Test
    fun `diffIds handles empty current set`() {
        val currentIds = emptySet<Long>()
        val indexedIds = setOf(1L, 2L, 3L)

        val diff = AlbumChangeDetector.diffIds(currentIds, indexedIds)

        assertTrue(diff.addedIds.isEmpty())
        assertEquals(setOf(1L, 2L, 3L), diff.removedIds)
    }

    @Test
    fun `diffIds handles empty indexed set`() {
        val currentIds = setOf(1L, 2L, 3L)
        val indexedIds = emptySet<Long>()

        val diff = AlbumChangeDetector.diffIds(currentIds, indexedIds)

        assertEquals(setOf(1L, 2L, 3L), diff.addedIds)
        assertTrue(diff.removedIds.isEmpty())
    }

    @Test
    fun `diffIds handles both empty sets`() {
        val diff = AlbumChangeDetector.diffIds(emptySet(), emptySet())

        assertFalse(diff.hasChanges)
        assertEquals(0, diff.addedCount)
        assertEquals(0, diff.removedCount)
    }

    @Test
    fun `diffIds handles large sets`() {
        val currentIds = (1L..10000L).toSet()
        val indexedIds = (1L..8000L).toSet()

        val diff = AlbumChangeDetector.diffIds(currentIds, indexedIds)

        assertEquals(2000, diff.addedCount)
        assertEquals(0, diff.removedCount)
    }

    // ============ detect 完整检测测试 ============

    @Test
    fun `detect returns correct change with added photos`() {
        val album = mockAlbum()
        val currentPhotos = listOf(
            mockPhoto(1), mockPhoto(2), mockPhoto(3), mockPhoto(4)
        )
        val indexedIds = setOf(1L, 2L, 3L)

        val change = AlbumChangeDetector.detect(album, currentPhotos, indexedIds)

        assertEquals(album, change.album)
        assertEquals(1, change.addedCount)
        assertEquals(4L, change.addedPhotos[0].id)
        assertTrue(change.removedPhotoIds.isEmpty())
        assertTrue(change.hasChanges)
    }

    @Test
    fun `detect returns correct change with removed photos`() {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2))
        val indexedIds = setOf(1L, 2L, 3L, 4L)

        val change = AlbumChangeDetector.detect(album, currentPhotos, indexedIds)

        assertTrue(change.addedPhotos.isEmpty())
        assertEquals(setOf(3L, 4L), change.removedPhotoIds)
        assertEquals(2, change.removedCount)
        assertTrue(change.hasChanges)
    }

    @Test
    fun `detect returns correct change with both add and remove`() {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2), mockPhoto(5), mockPhoto(6))
        val indexedIds = setOf(1L, 2L, 3L, 4L)

        val change = AlbumChangeDetector.detect(album, currentPhotos, indexedIds)

        assertEquals(2, change.addedCount)
        assertEquals(listOf(5L, 6L), change.addedPhotos.map { it.id })
        assertEquals(setOf(3L, 4L), change.removedPhotoIds)
        assertEquals(4, change.totalChangeCount)
    }

    @Test
    fun `detect returns no change when perfectly synced`() {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2), mockPhoto(3))
        val indexedIds = setOf(1L, 2L, 3L)

        val change = AlbumChangeDetector.detect(album, currentPhotos, indexedIds)

        assertFalse(change.hasChanges)
        assertEquals(0, change.totalChangeCount)
    }

    @Test
    fun `detect handles empty album`() {
        val album = mockAlbum()
        val currentPhotos = emptyList<Photo>()
        val indexedIds = setOf(1L, 2L, 3L)

        val change = AlbumChangeDetector.detect(album, currentPhotos, indexedIds)

        assertTrue(change.addedPhotos.isEmpty())
        assertEquals(setOf(1L, 2L, 3L), change.removedPhotoIds)
        assertTrue(change.hasChanges)
    }

    @Test
    fun `detect handles new album with no prior index`() {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2), mockPhoto(3))
        val indexedIds = emptySet<Long>()

        val change = AlbumChangeDetector.detect(album, currentPhotos, indexedIds)

        assertEquals(3, change.addedCount)
        assertTrue(change.removedPhotoIds.isEmpty())
        assertEquals(listOf(1L, 2L, 3L), change.addedPhotos.map { it.id })
    }

    // ============ detectBatch 批量检测测试 ============

    @Test
    fun `detectBatch handles multiple albums`() {
        val album1 = mockAlbum(id = 1, label = "Camera")
        val album2 = mockAlbum(id = 2, label = "Screenshots")

        val currentPhotosByAlbum = mapOf(
            1L to listOf(mockPhoto(1, 1), mockPhoto(2, 1), mockPhoto(3, 1)),
            2L to listOf(mockPhoto(10, 2), mockPhoto(11, 2))
        )
        val indexedIdsByAlbum = mapOf(
            1L to setOf(1L, 2L),
            2L to setOf(10L, 11L, 12L)
        )

        val changes = AlbumChangeDetector.detectBatch(
            listOf(album1, album2),
            currentPhotosByAlbum,
            indexedIdsByAlbum
        )

        assertEquals(2, changes.size)

        // Album 1: added photo 3, no removals
        val change1 = changes[0]
        assertEquals(1, change1.addedCount)
        assertEquals(3L, change1.addedPhotos[0].id)
        assertTrue(change1.removedPhotoIds.isEmpty())

        // Album 2: no additions, removed photo 12
        val change2 = changes[1]
        assertTrue(change2.addedPhotos.isEmpty())
        assertEquals(setOf(12L), change2.removedPhotoIds)
    }

    @Test
    fun `detectBatch handles missing album data gracefully`() {
        val album1 = mockAlbum(id = 1)
        val album2 = mockAlbum(id = 2)

        // Only provide data for album1
        val currentPhotosByAlbum = mapOf(1L to listOf(mockPhoto(1, 1)))
        val indexedIdsByAlbum = mapOf(1L to setOf(1L))

        val changes = AlbumChangeDetector.detectBatch(
            listOf(album1, album2),
            currentPhotosByAlbum,
            indexedIdsByAlbum
        )

        assertEquals(2, changes.size)
        // Album 1: no changes
        assertFalse(changes[0].hasChanges)
        // Album 2: no data → no indexed IDs, 1 current photo → 1 addition
        assertEquals(1, changes[1].addedCount)
    }
}

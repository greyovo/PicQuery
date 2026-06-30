package me.grey.picquery.domain

import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.ObjectBoxEmbeddingRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.data.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AlbumUpdateService] 单元测试
 *
 * 使用 mockk 模拟所有外部依赖，测试增量更新编排逻辑。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumUpdateServiceTest {

    private lateinit var photoRepository: PhotoRepository
    private lateinit var objectBoxRepo: ObjectBoxEmbeddingRepository
    private lateinit var roomEmbeddingRepo: EmbeddingRepository
    private lateinit var albumRepository: AlbumRepository
    private lateinit var embeddingService: EmbeddingService
    private lateinit var service: AlbumUpdateService

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        photoRepository = mockk(relaxed = true)
        objectBoxRepo = mockk(relaxed = true)
        roomEmbeddingRepo = mockk(relaxed = true)
        albumRepository = mockk(relaxed = true)
        embeddingService = mockk(relaxed = true)

        service = AlbumUpdateService(
            photoRepository = photoRepository,
            objectBoxEmbeddingRepository = objectBoxRepo,
            embeddingRepository = roomEmbeddingRepo,
            albumRepository = albumRepository,
            embeddingService = embeddingService,
            dispatcher = testDispatcher
        )
    }

    private fun mockAlbum(id: Long = 1L, label: String = "Camera") = Album(
        id = id,
        label = label,
        coverPath = "/path/to/cover",
        timestamp = 1000L,
        count = 5
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

    // ============ detectChanges 测试 ============

    @Test
    fun `detectChanges returns correct change for added photos`() = runTest {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2), mockPhoto(3), mockPhoto(4))
        val indexedIds = listOf(1L, 2L, 3L)

        coEvery { photoRepository.getPhotoListByAlbumId(album.id) } returns currentPhotos
        every { objectBoxRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds
        every { roomEmbeddingRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds

        val change = service.detectChanges(album)

        assertEquals(album, change.album)
        assertEquals(1, change.addedCount)
        assertEquals(4L, change.addedPhotos[0].id)
        assertTrue(change.removedPhotoIds.isEmpty())
    }

    @Test
    fun `detectChanges returns correct change for removed photos`() = runTest {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2))
        val indexedIds = listOf(1L, 2L, 3L, 4L)

        coEvery { photoRepository.getPhotoListByAlbumId(album.id) } returns currentPhotos
        every { objectBoxRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds
        every { roomEmbeddingRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds

        val change = service.detectChanges(album)

        assertTrue(change.addedPhotos.isEmpty())
        assertEquals(setOf(3L, 4L), change.removedPhotoIds)
    }

    @Test
    fun `detectChanges returns no change when synced`() = runTest {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2), mockPhoto(3))
        val indexedIds = listOf(1L, 2L, 3L)

        coEvery { photoRepository.getPhotoListByAlbumId(album.id) } returns currentPhotos
        every { objectBoxRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds
        every { roomEmbeddingRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds

        val change = service.detectChanges(album)

        assertFalse(change.hasChanges)
    }

    // ============ applyUpdate 测试 ============

    @Test
    fun `applyUpdate returns NoChange when no changes`() = runTest {
        val album = mockAlbum()
        val change = AlbumChange(album, addedPhotos = emptyList(), removedPhotoIds = emptySet())

        val result = service.applyUpdate(change)

        assertTrue(result is AlbumUpdateResult.NoChange)
        // Should not call any encoding or deletion
        coVerify(exactly = 0) { embeddingService.encodePhotoList(any(), any()) }
        verify(exactly = 0) { objectBoxRepo.removeByPhotoIds(any()) }
    }

    @Test
    fun `applyUpdate encodes new photos and does not delete when only additions`() = runTest {
        val album = mockAlbum()
        val addedPhotos = listOf(mockPhoto(4), mockPhoto(5))
        val change = AlbumChange(album, addedPhotos = addedPhotos, removedPhotoIds = emptySet())

        coEvery { embeddingService.encodePhotoList(any(), any()) } returns true
        every { photoRepository.getImageCountInAlbum(album.id) } returns 5

        val result = service.applyUpdate(change)

        assertTrue(result is AlbumUpdateResult.Success)
        val success = result as AlbumUpdateResult.Success
        assertEquals(2, success.addedCount)
        assertEquals(0, success.removedCount)
        assertTrue(success.allEncoded)

        // Verify encode was called with added photos
        coVerify { embeddingService.encodePhotoList(addedPhotos, any()) }
        // Verify no deletion happened
        verify(exactly = 0) { objectBoxRepo.removeByPhotoIds(any()) }
        verify(exactly = 0) { roomEmbeddingRepo.removeByPhotoIds(any()) }
        // Verify album metadata was updated
        verify { albumRepository.addSearchableAlbum(any()) }
    }

    @Test
    fun `applyUpdate deletes stale embeddings and does not encode when only removals`() = runTest {
        val album = mockAlbum()
        val removedIds = setOf(3L, 4L, 5L)
        val change = AlbumChange(album, addedPhotos = emptyList(), removedPhotoIds = removedIds)

        every { photoRepository.getImageCountInAlbum(album.id) } returns 2

        val result = service.applyUpdate(change)

        assertTrue(result is AlbumUpdateResult.Success)
        val success = result as AlbumUpdateResult.Success
        assertEquals(0, success.addedCount)
        assertEquals(3, success.removedCount)
        assertTrue(success.allEncoded)

        // Verify deletion was called on both repos with matching content
        verify {
            objectBoxRepo.removeByPhotoIds(match { it.contentEquals(removedIds.toLongArray()) })
        }
        verify {
            roomEmbeddingRepo.removeByPhotoIds(match { it.contentEquals(removedIds.toLongArray()) })
        }
        // Verify no encoding happened
        coVerify(exactly = 0) { embeddingService.encodePhotoList(any(), any()) }
        // Verify album metadata was updated
        verify { albumRepository.addSearchableAlbum(any()) }
    }

    @Test
    fun `applyUpdate handles both additions and removals`() = runTest {
        val album = mockAlbum()
        val addedPhotos = listOf(mockPhoto(5), mockPhoto(6))
        val removedIds = setOf(3L, 4L)
        val change = AlbumChange(album, addedPhotos = addedPhotos, removedPhotoIds = removedIds)

        coEvery { embeddingService.encodePhotoList(any(), any()) } returns true
        every { photoRepository.getImageCountInAlbum(album.id) } returns 4

        val result = service.applyUpdate(change)

        assertTrue(result is AlbumUpdateResult.Success)
        val success = result as AlbumUpdateResult.Success
        assertEquals(2, success.addedCount)
        assertEquals(2, success.removedCount)
        assertTrue(success.allEncoded)

        // Verify both encoding and deletion happened
        coVerify { embeddingService.encodePhotoList(addedPhotos, any()) }
        verify {
            objectBoxRepo.removeByPhotoIds(match { it.contentEquals(removedIds.toLongArray()) })
        }
        verify {
            roomEmbeddingRepo.removeByPhotoIds(match { it.contentEquals(removedIds.toLongArray()) })
        }
    }

    @Test
    fun `applyUpdate handles encoding failure gracefully`() = runTest {
        val album = mockAlbum()
        val addedPhotos = listOf(mockPhoto(5))
        val change = AlbumChange(album, addedPhotos = addedPhotos, removedPhotoIds = emptySet())

        // Simulate encoding failure (encoder busy)
        coEvery { embeddingService.encodePhotoList(any(), any()) } returns false

        val result = service.applyUpdate(change)

        assertTrue(result is AlbumUpdateResult.Success)
        val success = result as AlbumUpdateResult.Success
        assertEquals(0, success.addedCount)
        assertFalse(success.allEncoded)

        // Album metadata should NOT be updated when encoding fails
        verify(exactly = 0) { albumRepository.addSearchableAlbum(any()) }
    }

    @Test
    fun `applyUpdate continues with removals even if encoding fails`() = runTest {
        val album = mockAlbum()
        val addedPhotos = listOf(mockPhoto(5))
        val removedIds = setOf(3L, 4L)
        val change = AlbumChange(album, addedPhotos = addedPhotos, removedPhotoIds = removedIds)

        coEvery { embeddingService.encodePhotoList(any(), any()) } returns false

        val result = service.applyUpdate(change)

        assertTrue(result is AlbumUpdateResult.Success)
        val success = result as AlbumUpdateResult.Success
        assertEquals(0, success.addedCount)
        assertEquals(2, success.removedCount) // Removals still happened
        assertFalse(success.allEncoded)

        // Verify deletion still happened
        verify {
            objectBoxRepo.removeByPhotoIds(match { it.contentEquals(removedIds.toLongArray()) })
        }
        verify {
            roomEmbeddingRepo.removeByPhotoIds(match { it.contentEquals(removedIds.toLongArray()) })
        }
    }

    // ============ updateAlbum (便捷方法) 测试 ============

    @Test
    fun `updateAlbum detects and applies changes end-to-end`() = runTest {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2), mockPhoto(5))
        val indexedIds = listOf(1L, 2L, 3L) // 3 removed, 5 added

        coEvery { photoRepository.getPhotoListByAlbumId(album.id) } returns currentPhotos
        every { objectBoxRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds
        every { roomEmbeddingRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds
        coEvery { embeddingService.encodePhotoList(any(), any()) } returns true
        every { photoRepository.getImageCountInAlbum(album.id) } returns 3

        val result = service.updateAlbum(album)

        assertTrue(result is AlbumUpdateResult.Success)
        val success = result as AlbumUpdateResult.Success
        assertEquals(1, success.addedCount)
        assertEquals(1, success.removedCount)

        // Verify remove was called for photo 3
        verify {
            objectBoxRepo.removeByPhotoIds(match { it.contentEquals(longArrayOf(3L)) })
        }
        // Verify encode was called with photo 5
        coVerify {
            embeddingService.encodePhotoList(
                match { photos -> photos.size == 1 && photos[0].id == 5L },
                any()
            )
        }
    }

    @Test
    fun `updateAlbum returns NoChange when album is already synced`() = runTest {
        val album = mockAlbum()
        val currentPhotos = listOf(mockPhoto(1), mockPhoto(2), mockPhoto(3))
        val indexedIds = listOf(1L, 2L, 3L)

        coEvery { photoRepository.getPhotoListByAlbumId(album.id) } returns currentPhotos
        every { objectBoxRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds
        every { roomEmbeddingRepo.getPhotoIdsByAlbumId(album.id) } returns indexedIds

        val result = service.updateAlbum(album)

        assertTrue(result is AlbumUpdateResult.NoChange)
        coVerify(exactly = 0) { embeddingService.encodePhotoList(any(), any()) }
        verify(exactly = 0) { objectBoxRepo.removeByPhotoIds(any()) }
    }
}

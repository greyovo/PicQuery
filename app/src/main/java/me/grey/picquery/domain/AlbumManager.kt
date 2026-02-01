@file:OptIn(ExperimentalPermissionsApi::class)

package me.grey.picquery.domain

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.grey.picquery.PicQueryApplication.Companion.context
import me.grey.picquery.R
import me.grey.picquery.common.showToast
import me.grey.picquery.data.data_source.AlbumRepository
import me.grey.picquery.data.data_source.EmbeddingRepository
import me.grey.picquery.data.data_source.PhotoRepository
import me.grey.picquery.data.model.Album
import me.grey.picquery.ui.albums.EncodingState
import timber.log.Timber

class AlbumManager(
    private val albumRepository: AlbumRepository,
    private val photoRepository: PhotoRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val imageSearcher: ImageSearcher,
    private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "AlbumManager"
    }

    val encodingState = mutableStateOf(EncodingState())

    val isEncoderBusy: Boolean
        get() = encodingState.value.isBusy

    private val albumList = mutableStateListOf<Album>()
    private val _searchableAlbumList = MutableStateFlow<List<Album>>(emptyList())
    private val _unsearchableAlbumList = MutableStateFlow<List<Album>>(emptyList())

    val searchableAlbumList: StateFlow<List<Album>> = _searchableAlbumList.asStateFlow()
    val unsearchableAlbumList: StateFlow<List<Album>> = _unsearchableAlbumList.asStateFlow()

    val albumsToEncode = mutableStateListOf<Album>()

    private fun searchableAlbumFlow() = albumRepository.getSearchableAlbumFlow()

    private var initialized = false

    fun getAlbumList() = albumList

    private val managerScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineExceptionHandler { _, exception ->
                // Handle coroutine exceptions
                Timber.tag("AlbumManager").e(exception, "Coroutine error")
            }
    )

    fun processAlbums(snapshot: List<Album>) {
        managerScope.launch {
            encodeAlbums(snapshot)
            initDataFlow()
        }
    }

    suspend fun initAllAlbumList() {
        if (initialized) return
        withContext(ioDispatcher) {
            // Get all albums from device
            val albums = albumRepository.getAllAlbums()
            albumList.addAll(albums)
            Timber.tag(TAG).d("ALL albums: ${albums.size}")
            this@AlbumManager.initialized = true
            initDataFlow()
        }
    }

    suspend fun initDataFlow() {
        searchableAlbumFlow().collect {
            // Retrieve indexed albums from database
            // Some albums may have been indexed but deleted, so filter from all albums
            val res = it.toMutableList().sortedByDescending { album: Album -> album.count }
            _searchableAlbumList.update { res }
            Timber.tag(TAG).d("Searchable albums: ${it.size}")
            // Unsearchable albums = all albums - indexed albums
            val unsearchable = albumList.filter { all -> !it.contains(all) }

            _unsearchableAlbumList.update { (unsearchable.toMutableList().sortedByDescending { album: Album -> album.count }) }
            Timber.tag(TAG).d("Unsearchable albums: ${unsearchable.size}")
        }
    }

    fun toggleAlbumSelection(album: Album) {
        if (albumsToEncode.contains(album)) {
            albumsToEncode.remove(album)
        } else {
            albumsToEncode.add(album)
        }
    }

    fun toggleSelectAllAlbums() {
        if (albumsToEncode.size != unsearchableAlbumList.value.size) {
            albumsToEncode.clear()
            albumsToEncode.addAll(unsearchableAlbumList.value)
        } else {
            albumsToEncode.clear()
        }
    }

    /**
     * Get photo flow for multiple albums
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getPhotosFlow(albums: List<Album>) = albums.asFlow()
        .flatMapConcat { album ->
            photoRepository.getPhotoListByAlbumIdPaginated(album.id)
        }

    /**
     * Get total photo count for album list
     */
    private suspend fun getTotalPhotoCount(albums: List<Album>): Int = withContext(ioDispatcher) {
        albums.sumOf { album -> photoRepository.getImageCountInAlbum(album.id) }
    }

    suspend fun encodeAlbums(albums: List<Album>) {
        if (albums.isEmpty()) {
            showToast(context.getString(R.string.no_album_selected))
            return
        }

        encodingState.value =
            EncodingState(status = EncodingState.Status.Loading)

        try {
            val totalPhotos = getTotalPhotoCount(albums)
            val processedPhotos = AtomicInteger(0)
            var success = true

            getPhotosFlow(albums).collect { photoChunk ->

                val chunkSuccess = imageSearcher.encodePhotoListV2(photoChunk) { cur, total, cost ->
                    Timber.tag(TAG).d("Encoded $cur/$total photos, cost: $cost")
                    processedPhotos.addAndGet(cur)
                    encodingState.value = encodingState.value.copy(
                        current = processedPhotos.get(),
                        total = totalPhotos,
                        cost = cost,
                        status = EncodingState.Status.Indexing
                    )
                }

                if (!chunkSuccess) {
                    success = false
                    Timber.tag(TAG).w("Failed to encode photo chunk, size: ${photoChunk.size}")
                }
            }

            if (success) {
                Timber.tag(TAG).i("Encoded ${albums.size} album(s) with $totalPhotos photos!")
                withContext(ioDispatcher) {
                    albumRepository.addAllSearchableAlbum(albums)
                }
                encodingState.value = encodingState.value.copy(
                    status = EncodingState.Status.Finish
                )
            } else {
                Timber.tag(TAG).w("encodePhotoList failed! Maybe too much request.")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error encoding albums")
            encodingState.value = encodingState.value.copy(
                status = EncodingState.Status.Error
            )
        }
    }

    fun clearIndexingState() {
        encodingState.value = EncodingState()
    }

    fun removeSingleAlbumIndex(album: Album) {
        embeddingRepository.removeByAlbum(album)
        albumRepository.removeSearchableAlbum(album)
    }
}

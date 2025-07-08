package me.grey.picquery.ui.photoDetail

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import java.io.File
import kotlin.collections.isNotEmpty
import me.grey.picquery.R
import me.grey.picquery.data.model.Photo
import me.grey.picquery.ui.simlilar.SimilarPhotosViewModel
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalGlideComposeApi::class
)
@Composable
fun PhotoDetailScreen(
    onNavigateBack: () -> Unit,
    initialPage: Int = 0,
    groupIndex: Int = 0,
    photoDetailViewModel: SimilarPhotosViewModel = koinViewModel()
) {
    val photoList by photoDetailViewModel.selectedPhotos.collectAsState()
    LaunchedEffect(groupIndex) {
        photoDetailViewModel.getPhotosFromGroup(groupIndex)
    }

    val safeInitialPage = if (initialPage < photoList.size) initialPage else 0
    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { photoList.size }
    )

    // Ensure pagerState.currentPage is always within bounds of photoList
    LaunchedEffect(photoList.size) {
        if (photoList.isNotEmpty() && pagerState.currentPage >= photoList.size) {
            pagerState.animateScrollToPage(0)
        }
    }

    val externalAlbumLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Timber.d("Selected image uri: $uri")
            }
        }
    }

    PhotoDetailScreenContent(
        photoList = photoList,
        pagerState = pagerState,
        onNavigateBack = onNavigateBack,
        externalAlbumLauncher = externalAlbumLauncher
    )
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalGlideComposeApi::class
)
@Composable
private fun PhotoDetailScreenContent(
    photoList: List<Photo>,
    pagerState: PagerState,
    onNavigateBack: () -> Unit,
    externalAlbumLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val currentPhoto = if (photoList.isNotEmpty() && pagerState.currentPage < photoList.size) {
        photoList[pagerState.currentPage]
    } else {
        null
    }

    Scaffold(
        topBar = {
            PhotoDetailTopBar(
                currentPhoto = currentPhoto,
                onNavigateBack = onNavigateBack,
                onOpenExternal = {
                    val editIntent = Intent(Intent.ACTION_EDIT).apply {
                        setDataAndType(currentPhoto?.uri, "image/*")
                    }
                    externalAlbumLauncher.launch(editIntent)
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            PhotoPager(
                photoList = photoList,
                pagerState = pagerState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoDetailTopBar(currentPhoto: Photo?, onNavigateBack: () -> Unit, onOpenExternal: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = if (currentPhoto != null) {
                    stringResource(R.string.photo_details_with_id, currentPhoto.id)
                } else {
                    stringResource(R.string.photo_details)
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onOpenExternal) {
                Icon(Icons.Filled.OpenWith, contentDescription = "Open in external app")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
private fun PhotoPager(photoList: List<Photo>, pagerState: PagerState) {
    HorizontalPager(state = pagerState) { index ->
        if (index < photoList.size) {
            val photo = photoList[index]
            GlideImage(
                model = File(photo.path),
                contentDescription = photo.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

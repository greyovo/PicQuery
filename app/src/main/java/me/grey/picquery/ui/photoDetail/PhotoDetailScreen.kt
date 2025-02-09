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
import me.grey.picquery.R
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class,
    ExperimentalGlideComposeApi::class
)
@Composable
fun PhotoDetailScreen(
    onNavigateBack: () -> Unit,
    initialPage: Int = 0,
    photoDetailViewModel: PhotoDetailViewModel = koinViewModel()
) {

    LaunchedEffect(initialPage) {
        photoDetailViewModel.loadPhotosFromGroup(initialPage)
    }

    val photoList by photoDetailViewModel.photoList.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { photoList.size }
    )

    val externalAlbumLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Timber.d("Selected image uri: $uri")
            }
        }
    }

    Scaffold(
        topBar = {
            val currentPhoto = if (photoList.isNotEmpty()) photoList[pagerState.currentPage] else null
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

                    IconButton(
                        onClick = {
                            val editIntent = Intent(Intent.ACTION_EDIT).apply {
                                setDataAndType(currentPhoto!!.uri, "image/*")
                            }
                            externalAlbumLauncher.launch(editIntent)
                        }
                    ) {
                        Icon(Icons.Filled.OpenWith, contentDescription = "")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            HorizontalPager(state = pagerState) { index ->
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
}
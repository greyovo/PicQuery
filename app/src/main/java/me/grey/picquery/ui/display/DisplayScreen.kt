package me.grey.picquery.ui.display

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import me.grey.picquery.common.InitializeEffect
import me.grey.picquery.data.model.Photo
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DisplayScreen(
    initialPage: Int,
    displayViewModel: DisplayViewModel = koinViewModel()
) {
    val photoList = remember { displayViewModel.photoList }
    val pagerState = rememberPagerState(
        initialPage = 0,
        initialPageOffsetFraction = 0f,
        pageCount = { photoList.size }
    )
    InitializeEffect {
        displayViewModel.loadPhotos(initialPage)
        pagerState.scrollToPage(initialPage)
    }

    Surface {
        HorizontalPager(state = pagerState) { index ->
            ZoomablePagerImage(photo = photoList[index]) {

            }
        }
    }
}


// TODO 标明出处
@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun ZoomablePagerImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    maxScale: Float = 5f,
//    maxImageSize: Int,
    onItemClick: () -> Unit
) {
    val zoomState = rememberZoomState(maxScale = maxScale)
//    val painter = rememberAsyncImagePainter(
//        model = ImageRequest.Builder(LocalContext.current)
//            .data(media.uri)
//            .memoryCacheKey("media_${media.label}_${media.id}")
//            .diskCacheKey("media_${media.label}_${media.id}")
//            .size(maxImageSize)
//            .build(),
//        contentScale = ContentScale.Fit,
//        filterQuality = FilterQuality.None,
//        onSuccess = {
//            zoomState.setContentSize(it.painter.intrinsicSize)
//        }
//    )

//    LaunchedEffect(zoomState.scale) {
//        scrollEnabled.value = zoomState.scale == 1f
//    }

    GlideImage(
        modifier = modifier
            .fillMaxSize()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onDoubleClick = {},
                onClick = onItemClick
            )
            .zoomable(zoomState = zoomState),
        model = File(photo.path),
        contentDescription = photo.label,
        contentScale = ContentScale.Fit,
    )

//    Image(
//        modifier = modifier
//            .fillMaxSize()
//            .combinedClickable(
//                interactionSource = remember { MutableInteractionSource() },
//                indication = null,
//                onDoubleClick = {},
//                onClick = onItemClick
//            )
//            .zoomable(
//                zoomState = zoomState,
//            ),
//        painter = painter,
//        contentScale = ContentScale.Fit,
//        contentDescription = photo.label
//    )
}
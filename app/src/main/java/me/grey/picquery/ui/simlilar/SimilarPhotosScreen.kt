package me.grey.picquery.ui.simlilar

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import java.io.File
import me.grey.picquery.R
import me.grey.picquery.data.model.Photo
import me.grey.picquery.ui.common.BackButton
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarPhotosScreen(
    onNavigateBack: () -> Unit,
    onPhotoClick: (Int, Int, List<Photo>) -> Unit,
    onConfigUpdate: (Float, Float, Int) -> Unit,
    modifier: Modifier = Modifier,
    similarPhotosViewModel: SimilarPhotosViewModel = koinViewModel()
) {
    val uiState by similarPhotosViewModel.uiState.collectAsState()
    val configuration = LocalSimilarityConfig.current
    var showConfigBottomSheet by remember { mutableStateOf(false) }

    val lastConfiguration by remember {
        mutableStateOf(
            SimilarityConfiguration(
                searchImageSimilarityThreshold = configuration.searchImageSimilarityThreshold,
                similarityGroupDelta = configuration.similarityGroupDelta
            )
        )
    }

    LaunchedEffect(
        configuration.searchImageSimilarityThreshold,
        configuration.similarityGroupDelta
    ) {
        if (configuration.searchImageSimilarityThreshold != lastConfiguration.searchImageSimilarityThreshold ||
            configuration.similarityGroupDelta != lastConfiguration.similarityGroupDelta
        ) {
            similarPhotosViewModel.resetState()
//            similarPhotosViewModel.updateSimilarityConfiguration(
//                searchImageSimilarityThreshold = configuration.searchImageSimilarityThreshold,
//                similarityDelta = configuration.similarityGroupDelta
//            )
//            lastConfiguration = SimilarityConfiguration(
//                searchImageSimilarityThreshold = configuration.searchImageSimilarityThreshold,
//                similarityGroupDelta = configuration.similarityGroupDelta
//            )
        }
    }

    val hasLoadedInitially = remember { mutableStateOf(false) }

    LaunchedEffect(similarPhotosViewModel) {
        if (uiState is SimilarPhotosUiState.Loading && !hasLoadedInitially.value) {
            similarPhotosViewModel.findSimilarPhotos()
            hasLoadedInitially.value = true
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.similar_photos_title)) },
                navigationIcon = {
                    BackButton(onClick = onNavigateBack)
                },
                actions = {
                    IconButton(onClick = { showConfigBottomSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.similarity_config_title)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val state = uiState) {
                SimilarPhotosUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                SimilarPhotosUiState.Empty -> {
                    EmptyStateView(stringResource(R.string.similar_photos_empty_state))
                }

                is SimilarPhotosUiState.Success -> {
                    val handlePhotoClick: (Int, Int, List<Photo>) -> Unit = { groupIndex, photoIndex, photoGroup ->
                        onPhotoClick(groupIndex, photoIndex, photoGroup)
                    }
                    SimilarPhotosGroup(
                        modifier = Modifier.fillMaxSize(),
                        photos = state.similarPhotoGroups,
                        onPhotoClick = handlePhotoClick
                    )
                }

                is SimilarPhotosUiState.Error -> {
                    ErrorStateView(state)
                }
            }

            if (showConfigBottomSheet) {
                SimilarityConfigBottomSheet(
                    initialMinGroupSize = configuration.minSimilarityGroupSize,
                    onDismiss = { showConfigBottomSheet = false },
                    onConfigUpdate = { newSearchThreshold, newSimilarityDelta, newMinGroupSize ->
                        onConfigUpdate(
                            newSearchThreshold,
                            newSimilarityDelta,
                            newMinGroupSize
                        )
                        showConfigBottomSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ErrorStateView(state: SimilarPhotosUiState.Error) {
    when (state.type) {
        ErrorType.WORKER_TIMEOUT ->
            ErrorView("Calculation timed out", state.message)

        ErrorType.CALCULATION_FAILED ->
            ErrorView("Calculation error", state.message)

        ErrorType.NO_SIMILAR_PHOTOS ->
            EmptyStateView(stringResource(R.string.similar_photos_empty_state))

        else ->
            GenericErrorView(state.message)
    }
}

@Composable
fun SimilarPhotosGroup(
    modifier: Modifier = Modifier,
    photos: List<List<Photo>>,
    onPhotoClick: (Int, Int, List<Photo>) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        itemsIndexed(
            items = photos,
            key = { index, group -> "${index}_${group.firstOrNull()?.id ?: 0L}" }
        ) { groupIndex, photoGroup ->

            // Use first photo's modification time as group title
            val firstPhoto = photoGroup.firstOrNull()
            val groupTitle = firstPhoto?.let {
                DateUtils.getRelativeDateTimeString(
                    LocalContext.current,
                    it.timestamp * 1000,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.WEEK_IN_MILLIS,
                    DateUtils.FORMAT_SHOW_TIME
                ).toString()
            } ?: "Group ${groupIndex + 1}"

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    // Group header with clearer hierarchy
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = groupTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = stringResource(R.string.photo_group_count, photoGroup.size),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            enabled = false
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal scrollable row of photos in the group
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        itemsIndexed(
                            items = photoGroup,
                            key = { _, photo -> photo.id }
                        ) { photoIndex, photo ->
                            PhotoGroupItem(
                                photo = photo,
                                modifier = Modifier.size(152.dp),
                                onClick = { onPhotoClick(groupIndex, photoIndex, photoGroup) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun PhotoGroupItem(photo: Photo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick
            )
    ) {
        GlideImage(
            model = File(photo.path),
            contentDescription = photo.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
fun ErrorView(
    title: String,
    message: String? = null,
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )

            message?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            onRetry?.let { retry ->
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = retry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(message: String = "No photos to display", icon: ImageVector = Icons.Outlined.ImageNotSupported) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = message,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GenericErrorView(message: String? = "An unexpected error occurred", onRetry: (() -> Unit)? = null) {
    ErrorView(
        title = "Oops! Something went wrong",
        message = message,
        onRetry = onRetry
    )
}

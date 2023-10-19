package me.grey.picquery.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import me.grey.picquery.R
import me.grey.picquery.common.InitializeEffect
import org.koin.androidx.compose.koinViewModel
import java.lang.Float.min

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun UserGuide(
    modifier: Modifier,
    onRequestPermission: () -> Unit,
    onOpenAlbum: () -> Unit,
    onFinish: () -> Unit,
    state: UserGuideTaskState,
    homeViewModel: HomeViewModel = koinViewModel()
) {
    val mediaPermissions = rememberMediaPermissions()
    InitializeEffect() {
        if (mediaPermissions.allPermissionsGranted) {
            homeViewModel.doneRequestPermission()
        }
    }
    Column(modifier) {
        ListItem(
            leadingContent = {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    modifier = Modifier.size(40.dp),
                    contentDescription = "logo"
                )
            },
            headlineContent = {
                Text(
                    text = stringResource(R.string.welcome_to_picquery),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
            },
            supportingContent = { Text(text = stringResource(R.string.user_guide_tips)) },
        )

        // Step 1
        StepListItem(
            stepNumber = 1,
            enabled = true,
            finished = state.permissionDone,
            icon = Icons.Default.Key,
            title = stringResource(R.string.step_1_title),
            subtitle = stringResource(R.string.step_1_detail),
            onClick = { onRequestPermission() }
        )
        // Step 2
        StepListItem(
            stepNumber = 2,
            enabled = state.permissionDone,
            finished = state.indexDone,
            icon = Icons.Default.PhotoAlbum,
            title = stringResource(R.string.step_2_title),
            subtitle = stringResource(R.string.step_2_detail),
            onClick = { onOpenAlbum() }
        )

        // Step 3
        StepListItem(
            stepNumber = 3,
            enabled = state.permissionDone && state.indexDone,
            finished = false,
            icon = Icons.Default.Search,
            title = stringResource(R.string.step_3_title),
            subtitle = stringResource(R.string.step_3_detail),
            onClick = { onFinish() }
        )


        Box(modifier = Modifier.height(15.dp))
        Button(
            enabled = state.permissionDone,
            onClick = { onFinish() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            val text =
                if (state.allFinished) stringResource(R.string.i_got_it)
                else stringResource(R.string.skip)
            Text(text = text)
        }

    }
}

@Composable
private fun StepListItem(
    stepNumber: Int,
    enabled: Boolean,
    finished: Boolean,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val background =
        when {
            finished -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            !enabled -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    val color =
        when {
            finished -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f)
            !enabled -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }

    val textStyle = TextStyle(color = color)

    val finishIcon = @Composable {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = color
        )
    }
    OutlinedCard(
        modifier = Modifier
            .padding(vertical = 4.dp),
        border = BorderStroke(1.dp, background.copy(alpha = min(background.alpha + 0.1f, 1f))),
    ) {
        ListItem(
            modifier = Modifier.clickable(enabled = enabled) { onClick() },
            colors = ListItemDefaults.colors(
                containerColor = background
            ),
            leadingContent = {
                if (finished) {
                    finishIcon()
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                    )
                }
            },
            headlineContent = {
                Text(
                    text = title,
                    style = textStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
            },
            supportingContent = { Text(text = subtitle, style = textStyle) },
        )
    }
}
package me.grey.picquery.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.lang.Float.min

@Composable
fun UserGuide(
    modifier: Modifier,
    onRequestPermission: () -> Unit,
    onOpenAlbum: () -> Unit,
    onFinish: () -> Unit,
    currentStep: Int = 3,
) {
    Column(modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = "😊 欢迎使用",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
            },
            supportingContent = { Text(text = "跟随下列步骤简单操作后，即可快速搜索您的相册") },
        )

        // Step 1
        StepListItem(
            stepNumber = 1,
            currentStep = currentStep,
            icon = Icons.Default.Key,
            title = "1. 授予权限",
            subtitle = "本APP完全离线使用，请放心授权",
            onClick = { onRequestPermission() }
        )
        // Step 2
        StepListItem(
            stepNumber = 2,
            currentStep = currentStep,
            icon = Icons.Default.PhotoAlbum,
            title = "2. 索引相册",
            subtitle = "选择需要索引的相册，然后点击“索引”按钮",
            onClick = { onOpenAlbum() }
        )

        // Step 3
        StepListItem(
            stepNumber = 3,
            currentStep = currentStep,
            icon = Icons.Default.Search,
            title = "3. 开始搜索！",
            subtitle = "等待索引完成后，即可搜索需要的图片",
            onClick = {}
        )

        if (currentStep == 3) {
            Button(
                onClick = { onFinish() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(text = "我知道了")
            }
        }
    }
}

@Composable
private fun StepListItem(
    stepNumber: Int,
    currentStep: Int,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val background =
        when {
            currentStep > stepNumber -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            currentStep == stepNumber -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.background.copy(alpha = 0.3f)
        }
    val color =
        when {
            currentStep > stepNumber -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            currentStep == stepNumber -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
        }

    val textStyle = when {
//        currentStep > stepNumber -> TextStyle(color = color)
//        currentStep == stepNumber -> TextStyle(color = color, fontWeight = FontWeight.Bold)
        else -> TextStyle(color = color)
    }


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
        border = BorderStroke(1.dp, background.copy(alpha = min(background.alpha + 0.2f, 1f))),
//        colors = CardDefaults.outlinedCardColors(
//            containerColor = background,
//        )
    ) {
        ListItem(
            modifier = Modifier.clickable(enabled = currentStep == stepNumber) { onClick() },
            colors = ListItemDefaults.colors(
                containerColor = background
            ),
            leadingContent = {
                if (currentStep > stepNumber) {
                    finishIcon()
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                    )
                }
            },
            headlineContent = { Text(text = title, style = textStyle) },
            supportingContent = { Text(text = subtitle, style = textStyle) },
        )
    }
}
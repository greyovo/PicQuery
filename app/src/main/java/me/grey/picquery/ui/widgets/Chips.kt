package me.grey.picquery.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CustomChip(
    selected: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // define properties to the chip
    // such as color, shape, width
    Surface(
        color = when {
            selected -> MaterialTheme.colors.primary.copy(alpha = 0.1F)
            else -> Color.Transparent
        },
        contentColor = when {
            selected -> MaterialTheme.colors.onBackground
            else -> Color.LightGray
        },
        shape = CircleShape,
        modifier = modifier
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )

    }
}
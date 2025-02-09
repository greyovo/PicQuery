package me.grey.picquery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.grey.picquery.R

@Composable
fun ConfidenceTag(
    confidenceLevel: SearchResult.ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (confidenceLevel) {
        SearchResult.ConfidenceLevel.LOW ->
            stringResource(R.string.confidence_low) to Color(0xFFFF9800)  // Deep Red
        SearchResult.ConfidenceLevel.MEDIUM ->
            stringResource(R.string.confidence_medium) to Color(0xFFB3FF00)  // Amber
        SearchResult.ConfidenceLevel.HIGH ->
            stringResource(R.string.confidence_high) to Color(0xFF388E3C)  // Dark Green
    }

    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
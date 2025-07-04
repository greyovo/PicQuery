package me.grey.picquery.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.grey.picquery.R
import me.grey.picquery.domain.ImageSearcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchConfigBottomSheet(imageSearcher: ImageSearcher, onDismiss: () -> Unit) {
    var matchThreshold by remember { mutableStateOf(imageSearcher.matchThreshold.value) }
    var topK by remember { mutableStateOf(imageSearcher.topK.value) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.image_search_config_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(R.string.match_threshold_title) +
                    ": ${String.format("%.2f", matchThreshold)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.match_threshold_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = matchThreshold,
                onValueChange = { matchThreshold = it },
                valueRange = 0.1f..0.5f,
                steps = 20
            )

            Text(
                text = stringResource(R.string.top_k_results_title) +
                    ": $topK",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.top_k_results_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = topK.toFloat(),
                onValueChange = { topK = it.toInt() },
                valueRange = 10f..100f,
                steps = 9
            )

            Button(
                onClick = {
                    imageSearcher.updateSearchConfiguration(matchThreshold, topK)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.save_search_configuration))
            }
        }
    }
}

package me.grey.picquery.ui.simlilar

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.grey.picquery.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarityConfigBottomSheet(
    initialMinGroupSize: Int,
    onDismiss: () -> Unit,
    onConfigUpdate: (Float, Float, Int) -> Unit
) {
    val similarityConfiguration = LocalSimilarityConfig.current
    var searchThreshold by rememberSaveable { mutableStateOf(similarityConfiguration.searchImageSimilarityThreshold) }

    var similarityGroupDelta by rememberSaveable {
        mutableStateOf(similarityConfiguration.similarityGroupDelta)
    }

    val minGroupSize by rememberSaveable {
        mutableStateOf(initialMinGroupSize)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.similarity_config_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(R.string.search_image_similarity_threshold) +
                    ": ${"%.2f".format(Locale.getDefault(), searchThreshold)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.config_search_threshold_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = searchThreshold,
                onValueChange = { searchThreshold = it },
                valueRange = 0.90f..1.0f,
                steps = 20
            )

            Text(
                text = stringResource(R.string.similarity_group_delta) +
                    ": ${"%.2f".format(Locale.US, similarityGroupDelta)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.config_group_delta_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = similarityGroupDelta,
                onValueChange = { similarityGroupDelta = it },
                valueRange = 0.01f..0.1f,
                steps = 9
            )

            Text(
                text = stringResource(R.string.min_group_size) +
                    ": $minGroupSize",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    onConfigUpdate(
                        searchThreshold,
                        similarityGroupDelta,
                        minGroupSize
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.save_configuration))
            }
        }
    }
}

package me.grey.picquery.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.grey.picquery.R
import me.grey.picquery.common.calculateRemainingTime
import me.grey.picquery.domain.AlbumManager
import me.grey.picquery.ui.albums.EncodingState
import org.koin.compose.koinInject

@Composable
fun EncodingProgressBar(albumManager: AlbumManager = koinInject()) {
    val state by remember { albumManager.encodingState }
    var progress = (state.current.toDouble() / state.total).toFloat()
    if (progress.isNaN()) progress = 0.0f
    val finished = state.status == EncodingState.Status.Finish

    fun onClickOk() {
        albumManager.clearIndexingState()
    }

    AnimatedVisibility(visible = state.status != EncodingState.Status.None) {
        BottomAppBar {
            Column(
                Modifier
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.indexing_progress,
                            state.current,
                            state.total
                        )
                    )
                    val remain = calculateRemainingTime(
                        state.current,
                        state.total,
                        state.cost
                    )
                    TextButton(
                        onClick = { onClickOk() },
                        enabled = finished
                    ) {
                        Text(
                            text = if (finished) {
                                stringResource(R.string.finish_button)
                            } else {
                                stringResource(R.string.estimate_remain_time) +
                                    " ${DateUtils.formatElapsedTime(remain)}"
                            }
                        )
                    }
                }
                Box(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

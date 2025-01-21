package me.grey.picquery.common

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import me.grey.picquery.PicQueryApplication

private val context
    get() = PicQueryApplication.context


fun showToast(text: String, longToast: Boolean = false) {
    val duration = if (longToast) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    Toast.makeText(context, text, duration).show()
}

@Composable
fun InitializeEffect(block: suspend CoroutineScope.() -> Unit) {
    val initialized = rememberSaveable { mutableStateOf(false) }
    if (!initialized.value) {
        LaunchedEffect(Unit) {
            block.invoke(this)
            initialized.value = true
        }
    }
}


object Animation {
    /**
     * Value in ms
     */
    private const val DEFAULT_LOW_VELOCITY_SWIPE_DURATION = 150

    private const val DEFAULT_NAVIGATION_ANIMATION_DURATION = 300

    val navigateInAnimation = fadeIn(tween(DEFAULT_NAVIGATION_ANIMATION_DURATION))
    val navigateUpAnimation = fadeOut(tween(DEFAULT_NAVIGATION_ANIMATION_DURATION))

    val popInAnimation = slideInHorizontally { width -> width }
    val popUpAnimation = slideOutHorizontally { width -> -width }

    fun enterAnimation(durationMillis: Int): EnterTransition =
        fadeIn(tween(durationMillis))

    fun exitAnimation(durationMillis: Int): ExitTransition =
        fadeOut(tween(durationMillis))
}
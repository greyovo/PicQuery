package me.grey.picquery.common

import android.widget.Toast
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


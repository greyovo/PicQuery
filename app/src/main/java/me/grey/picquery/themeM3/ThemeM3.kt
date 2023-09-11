package me.grey.picquery.themeM3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography

val AppLightColorScheme = lightColorScheme(
    // M3 light Color parameters
)
val AppDarkColorScheme = darkColorScheme(
    // M3 dark Color parameters
)

val AppShapes = Shapes()

// Set of Material typography styles to start with
val AppTypography = Typography()

@Composable
fun PicQueryThemeM3(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        AppDarkColorScheme
    } else {
        AppLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
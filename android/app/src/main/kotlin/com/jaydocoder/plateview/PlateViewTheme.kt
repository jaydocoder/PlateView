package com.jaydocoder.plateview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF143A3A),
    secondary = Color(0xFF0D6D65),
    tertiary = Color(0xFFC78A2C),
    error = Color(0xFFBB3E3E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AD4CD),
    secondary = Color(0xFF78D0C4),
    tertiary = Color(0xFFFFD18C),
    error = Color(0xFFFFB4AB),
)

@Composable
fun PlateViewTheme(
    useDarkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}

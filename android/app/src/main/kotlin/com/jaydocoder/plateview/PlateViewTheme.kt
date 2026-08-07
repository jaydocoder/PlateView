package com.jaydocoder.plateview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 湖水蓝、松林绿、日照金和暮紫构成景区核验界面的多层次色彩。
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6277),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC5EFF2),
    onPrimaryContainer = Color(0xFF003640),
    secondary = Color(0xFF245B48),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCBEBD9),
    onSecondaryContainer = Color(0xFF003821),
    tertiary = Color(0xFF755B9C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF2C174E),
    error = Color(0xFFB64652),
    errorContainer = Color(0xFFFFD9DE),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF41000B),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF15262D),
    surface = Color(0xFFFCFDFE),
    onSurface = Color(0xFF15262D),
    surfaceVariant = Color(0xFFE3EEF0),
    onSurfaceVariant = Color(0xFF405158),
    outline = Color(0xFF70828A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D4E0),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF004E5E),
    onPrimaryContainer = Color(0xFFA7F1FB),
    secondary = Color(0xFF94D3B3),
    onSecondary = Color(0xFF003825),
    secondaryContainer = Color(0xFF174C39),
    onSecondaryContainer = Color(0xFFB0F0CC),
    tertiary = Color(0xFFD8BBFF),
    onTertiary = Color(0xFF3A225D),
    tertiaryContainer = Color(0xFF533C77),
    onTertiaryContainer = Color(0xFFECDDFF),
    error = Color(0xFFFFB2BD),
    errorContainer = Color(0xFF8E2D3B),
    onError = Color(0xFF650013),
    onErrorContainer = Color(0xFFFFD9DE),
    background = Color(0xFF0E171B),
    onBackground = Color(0xFFDDE7E9),
    surface = Color(0xFF121D21),
    onSurface = Color(0xFFDDE7E9),
    surfaceVariant = Color(0xFF3D4C52),
    onSurfaceVariant = Color(0xFFBBCBD0),
    outline = Color(0xFF85969C),
)

private val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
)

private val Shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun PlateViewTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}

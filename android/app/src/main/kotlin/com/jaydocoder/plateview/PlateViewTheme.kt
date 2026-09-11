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

private val LightColors = lightColorScheme(
    primary = Color(0xFF245B48),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCBEBD9),
    onPrimaryContainer = Color(0xFF003821),
    secondary = Color(0xFF3B8878),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7F0E8),
    onSecondaryContainer = Color(0xFF003C33),
    tertiary = Color(0xFF705B2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E6BA),
    onTertiaryContainer = Color(0xFF362A00),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F7F5),
    onBackground = Color(0xFF152B22),
    surface = Color(0xFFFCFDFC),
    onSurface = Color(0xFF152B22),
    surfaceVariant = Color(0xFFE7EFEA),
    onSurfaceVariant = Color(0xFF40564B),
    outline = Color(0xFF70827A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF94D3B3),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF174C39),
    onPrimaryContainer = Color(0xFFB0F0CC),
    secondary = Color(0xFF8BD5C3),
    onSecondary = Color(0xFF003C33),
    secondaryContainer = Color(0xFF155347),
    onSecondaryContainer = Color(0xFFB9F5E4),
    tertiary = Color(0xFFE6CF8F),
    onTertiary = Color(0xFF3A2E00),
    tertiaryContainer = Color(0xFF554719),
    onTertiaryContainer = Color(0xFFFFEAAF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1914),
    onBackground = Color(0xFFDCE9E1),
    surface = Color(0xFF14211B),
    onSurface = Color(0xFFDCE9E1),
    surfaceVariant = Color(0xFF2D3B34),
    onSurfaceVariant = Color(0xFFBDCABE),
    outline = Color(0xFF8B9C92),
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
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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

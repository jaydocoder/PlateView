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

// 景区车辆核验使用低饱和青绿，路牌黄只承担关键操作和状态强调。
private val LightColors = lightColorScheme(
    primary = Color(0xFF0D515B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCEE6E3),
    onPrimaryContainer = Color(0xFF002F35),
    secondary = Color(0xFF4E6467),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E8E6),
    onSecondaryContainer = Color(0xFF0A1F22),
    tertiary = Color(0xFF865A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA7),
    onTertiaryContainer = Color(0xFF2B1800),
    error = Color(0xFFB9464C),
    errorContainer = Color(0xFFFFDAD9),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410006),
    background = Color(0xFFF6FAF8),
    onBackground = Color(0xFF142429),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF142429),
    surfaceVariant = Color(0xFFE9F0EE),
    onSurfaceVariant = Color(0xFF3F4D4C),
    outline = Color(0xFF6E7B79),
)

// 深色模式保留道路标识的青绿和黄，避免高亮区域泛蓝。
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FD7D1),
    onPrimary = Color(0xFF00363C),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFB9F1EB),
    secondary = Color(0xFFB6CBC8),
    onSecondary = Color(0xFF213735),
    secondaryContainer = Color(0xFF384E4C),
    onSecondaryContainer = Color(0xFFD2E8E5),
    tertiary = Color(0xFFFFBA48),
    onTertiary = Color(0xFF452A00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDEA7),
    error = Color(0xFFFFB3B6),
    errorContainer = Color(0xFF922E36),
    onError = Color(0xFF65000B),
    onErrorContainer = Color(0xFFFFDAD9),
    background = Color(0xFF0E1719),
    onBackground = Color(0xFFDDE5E2),
    surface = Color(0xFF111C1E),
    onSurface = Color(0xFFDDE5E2),
    surfaceVariant = Color(0xFF3F4C4A),
    onSurfaceVariant = Color(0xFFBFCDCA),
    outline = Color(0xFF899694),
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
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
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

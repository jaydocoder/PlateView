package com.jaydocoder.plateview.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaydocoder.plateview.domain.vehicle.PlateQueryNormalizer
import com.jaydocoder.plateview.domain.vehicle.formatPlateForDisplay

@Composable
fun VehiclePlateBadge(
    plateNumber: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    compact: Boolean = false,
    plateColor: String? = null,
) {
    val shape = RoundedCornerShape(if (emphasized) 10.dp else 8.dp)
    val appearance = plateAppearance(plateColor, plateNumber)
    val horizontalPadding = when {
        emphasized -> 14.dp
        compact -> 10.dp
        else -> 10.dp
    }
    val verticalPadding = when {
        emphasized -> 7.dp
        compact -> 6.dp
        else -> 5.dp
    }
    val fontSize = when {
        emphasized -> 27.sp
        compact -> 18.sp
        else -> 16.sp
    }
    val minimumWidth = when {
        emphasized -> 164.dp
        compact -> 132.dp
        else -> 112.dp
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minimumWidth)
            .shadow(if (emphasized) 3.dp else 1.dp, shape, clip = false)
            .background(appearance.background, shape)
            .border(1.dp, appearance.border, shape)
            .semantics { contentDescription = appearance.accessibilityLabel }
            .testTag("vehicle_plate_badge"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatPlateForDisplay(plateNumber),
            modifier = Modifier.padding(PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding)),
            color = appearance.textColor,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

private fun plateAppearance(plateColor: String?, plateNumber: String): PlateAppearance = when (plateColor.normalizedPlateColor()) {
    PlateColor.Yellow -> PlateAppearance(
        background = Brush.verticalGradient(listOf(Color(0xFFE0A31B), Color(0xFFB87800))),
        border = Color(0xFFFFE39B).copy(alpha = 0.8f),
        textColor = Color.White,
        accessibilityLabel = "黄色号牌",
    )
    PlateColor.Green -> PlateAppearance(
        background = Brush.verticalGradient(listOf(Color(0xFF5DAF3C), Color(0xFF197343))),
        border = Color(0xFFB9E7A8).copy(alpha = 0.75f),
        textColor = Color.White,
        accessibilityLabel = "绿色号牌",
    )
    PlateColor.White -> PlateAppearance(
        background = Brush.verticalGradient(listOf(Color(0xFFF8F8F6), Color(0xFFE0E2DD))),
        border = Color(0xFF758078).copy(alpha = 0.72f),
        textColor = Color(0xFF17241E),
        accessibilityLabel = "白色号牌",
    )
    PlateColor.Black -> PlateAppearance(
        background = Brush.verticalGradient(listOf(Color(0xFF37413D), Color(0xFF151A18))),
        border = Color(0xFF93A09A).copy(alpha = 0.6f),
        textColor = Color.White,
        accessibilityLabel = "黑色号牌",
    )
    PlateColor.Blue -> bluePlateAppearance()
    null -> if (plateNumber.isNewEnergyPlate()) {
        plateAppearance("绿色", plateNumber)
    } else {
        bluePlateAppearance()
    }
}

private fun bluePlateAppearance() = PlateAppearance(
    background = Brush.verticalGradient(listOf(Color(0xFF155EAE), Color(0xFF073A7A))),
    border = Color(0xFFB8DEFF).copy(alpha = 0.66f),
    textColor = Color.White,
    accessibilityLabel = "蓝色号牌",
)

private fun String?.normalizedPlateColor(): PlateColor? = when (this?.trim()?.lowercase()) {
    "黄色", "黄", "黄牌", "yellow" -> PlateColor.Yellow
    "蓝色", "蓝", "蓝牌", "blue" -> PlateColor.Blue
    "绿色", "绿", "绿牌", "green" -> PlateColor.Green
    "白色", "白", "白牌", "white" -> PlateColor.White
    "黑色", "黑", "黑牌", "black" -> PlateColor.Black
    else -> null
}

private enum class PlateColor {
    Yellow,
    Blue,
    Green,
    White,
    Black,
}

private data class PlateAppearance(
    val background: Brush,
    val border: Color,
    val textColor: Color,
    val accessibilityLabel: String,
)

private fun String.isNewEnergyPlate(): Boolean {
    val normalized = PlateQueryNormalizer.normalize(this)
    return normalized.length == 8 && (normalized.getOrNull(2) in setOf('D', 'F') || normalized.lastOrNull() in setOf('D', 'F'))
}

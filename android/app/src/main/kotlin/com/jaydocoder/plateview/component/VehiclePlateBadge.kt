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
) {
    val shape = RoundedCornerShape(if (emphasized) 10.dp else 8.dp)
    val isNewEnergy = plateNumber.isNewEnergyPlate()
    val background = if (isNewEnergy) {
        Brush.verticalGradient(listOf(Color(0xFF5DAF3C), Color(0xFF197343)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF155EAE), Color(0xFF073A7A)))
    }
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
            .background(background, shape)
            .border(1.dp, Color(0xFFB8DEFF).copy(alpha = 0.66f), shape)
            .testTag("vehicle_plate_badge"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatPlateForDisplay(plateNumber),
            modifier = Modifier.padding(PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding)),
            color = Color.White,
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

private fun String.isNewEnergyPlate(): Boolean {
    val normalized = PlateQueryNormalizer.normalize(this)
    return normalized.length == 8 && (normalized.getOrNull(2) in setOf('D', 'F') || normalized.lastOrNull() in setOf('D', 'F'))
}

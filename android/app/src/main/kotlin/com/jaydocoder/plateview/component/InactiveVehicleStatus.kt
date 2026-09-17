package com.jaydocoder.plateview.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val StrictCheckContainerColor = Color(0xFFFFF1C7)
val StrictCheckContentColor = Color(0xFF6E4A00)
val InactiveVehicleContainerColor = Color(0xFFFFE9E8)
val InactiveVehicleContentColor = Color(0xFF8A1C22)
private val StrictCheckBadgeColor = Color(0xFFF2B93B)
private val StrictCheckBadgeContentColor = Color(0xFF3C2D00)
private val InactiveVehicleBadgeColor = Color(0xFFB83E4A)
private val InactiveVehicleBadgeContentColor = Color.White

@Composable
fun InactiveVehicleStatusBadge(status: String, modifier: Modifier = Modifier) {
    val (label, containerColor, contentColor) = when (status) {
        "STRICT_CHECK" -> Triple("严查", StrictCheckBadgeColor, StrictCheckBadgeContentColor)
        "BLACKLISTED" -> Triple("已拉黑", InactiveVehicleBadgeColor, InactiveVehicleBadgeContentColor)
        "INACTIVE" -> Triple("已停用（已失效）", InactiveVehicleBadgeColor, InactiveVehicleBadgeContentColor)
        else -> Triple("状态异常", InactiveVehicleBadgeColor, InactiveVehicleBadgeContentColor)
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(PaddingValues(horizontal = 8.dp, vertical = 3.dp)),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

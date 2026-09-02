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

val InactiveVehicleContainerColor = Color(0xFFFFF1C7)
val InactiveVehicleContentColor = Color(0xFF6E4A00)
private val InactiveVehicleBadgeColor = Color(0xFFB83E4A)
private val InactiveVehicleBadgeContentColor = Color.White

@Composable
fun InactiveVehicleStatusBadge(status: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = InactiveVehicleBadgeColor,
        contentColor = InactiveVehicleBadgeContentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = when (status) {
                "BLACKLISTED" -> "已拉黑"
                "INACTIVE" -> "已停用（已失效）"
                else -> "状态异常"
            },
            modifier = Modifier.padding(PaddingValues(horizontal = 8.dp, vertical = 3.dp)),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

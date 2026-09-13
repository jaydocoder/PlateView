package com.jaydocoder.plateview.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 使用稳定的 Row 承载横向选项，避免不同 Compose Foundation 版本间 FlowRow ABI 不一致。
 * 选项较多时允许横向滚动，保证每个选项仍可完整点击。
 */
@Composable
fun CompatFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = when (verticalArrangement) {
            Arrangement.Bottom -> androidx.compose.ui.Alignment.Bottom
            Arrangement.Center -> androidx.compose.ui.Alignment.CenterVertically
            else -> androidx.compose.ui.Alignment.Top
        },
    ) {
        content()
    }
}

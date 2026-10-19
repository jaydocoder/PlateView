package com.jaydocoder.plateview.feature.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jaydocoder.plateview.component.glass.GlassSurface
import com.jaydocoder.plateview.component.glass.GlassMaterial
import com.jaydocoder.plateview.component.glass.GlassSurfaceLevel

@Composable
fun UpdateAvailableAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        GlassSurface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            material = GlassMaterial.Lens,
            level = GlassSurfaceLevel.Inset,
            opacity = 0.28f,
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.testTag("update_available_action"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = "发现新版本，查看更新",
                )
            }
        }
        GlassSurface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(10.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary,
            material = GlassMaterial.Tinted,
            level = GlassSurfaceLevel.Inset,
            opacity = 0.86f,
            content = {},
        )
    }
}

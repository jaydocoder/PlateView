package com.jaydocoder.plateview.component.glass

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jaydocoder.plateview.PlateViewDimensions

@Composable
fun LiquidGlassScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                RoundedCornerShape(0.dp),
            ),
        content = content,
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
    elevated: Boolean = false,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    val alpha = if (elevated) {
        PlateViewDimensions.glassElevatedAlpha
    } else {
        PlateViewDimensions.glassSurfaceAlpha
    }
    Surface(
        modifier = modifier
            .shadow(if (elevated) 16.dp else 6.dp, shape, clip = false)
            .border(
                BorderStroke(
                    PlateViewDimensions.glassBorderWidth,
                    MaterialTheme.colorScheme.primary.copy(alpha = if (elevated) 0.22f else 0.12f),
                ),
                shape,
            ),
        shape = shape,
        color = color.copy(alpha = alpha),
        contentColor = contentColor,
        border = BorderStroke(
            PlateViewDimensions.glassBorderWidth,
            MaterialTheme.colorScheme.surface.copy(alpha = if (elevated) 0.86f else 0.58f),
        ),
        tonalElevation = if (elevated) 2.dp else 0.dp,
    ) {
        Box(content = content)
    }
}

@Composable
fun GlassPill(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
        label = "玻璃胶囊缩放",
    )
    val shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f) else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun GlassNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
        elevated = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun RowScope.GlassNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val lift by animateFloatAsState(
        targetValue = if (selected) -2f else 0f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 680f),
        label = "底栏图标位移",
    )
    NavigationBarItem(
        selected = selected,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .size(23.dp)
                    .graphicsLayer { translationY = lift },
            )
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
fun GlassActionSurface(
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)
    GlassSurface(
        modifier = modifier
            .clip(shape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        shape = shape,
        elevated = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ((minHeight - 24.dp) / 2).coerceAtLeast(8.dp)),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

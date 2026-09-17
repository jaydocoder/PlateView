package com.jaydocoder.plateview.component.glass

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jaydocoder.plateview.PlateViewDimensions
internal fun supportsLiquidBackdrop(
    sdkInt: Int,
    manufacturer: String,
    brand: String,
): Boolean = false

@Composable
fun LiquidGlassScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = GlassBackdrop(modifier, content)

@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // 所有设备使用静态玻璃，避开厂商 RenderThread 的实时背景采样崩溃。
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
    elevated: Boolean = false,
    color: Color = MaterialTheme.colorScheme.background,
    opacity: Float? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    val alpha = opacity?.coerceIn(0f, 1f) ?: if (elevated) 0.42f else 0.26f
    val tint = when {
        color == Color.Transparent -> Color.Transparent
        color == MaterialTheme.colorScheme.background || color == MaterialTheme.colorScheme.surface -> Color.White.copy(alpha = alpha)
        else -> color.copy(alpha = alpha)
    }
    // 背景材质在 Surface 外层绘制，必须先按同一形状裁剪，否则透明层会露出矩形边角。
    val material = modifier.clip(shape).background(tint)
    Surface(
        modifier = material,
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
        border = BorderStroke(
            PlateViewDimensions.glassBorderWidth,
            MaterialTheme.colorScheme.primary.copy(alpha = if (elevated) 0.28f else 0.16f),
        ),
        tonalElevation = 0.dp,
    ) {
        Box(content = content)
    }
}

@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) = GlassSurface(modifier, shape, elevated, content = content)

@Composable
fun LiquidGlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge)
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        elevated = true,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun LiquidGlassSegmentedControl(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        elevated = false,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(PlateViewDimensions.tinySpacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun LiquidGlassBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) = GlassNavigationBar(modifier, content)

@Composable
fun LiquidGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        GlassSurface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
            elevated = true,
            color = MaterialTheme.colorScheme.surface,
            opacity = 0.78f,
            content = content,
        )
    }
}

@Composable
fun GlassPill(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    transparent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 760f),
        label = "玻璃控件按压缩放",
    )
    val shape = RoundedCornerShape(PlateViewDimensions.cornerMedium)
    GlassSurface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .clickable(source, indication = null, role = Role.Tab) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        shape = shape,
        elevated = selected && !transparent,
        color = if (transparent) Color.Transparent else if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        opacity = if (transparent) 0f else if (selected) 0.54f else 0.28f,
        content = content,
    )
}

@Composable
fun LiquidGlassControl(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) = GlassPill(selected = selected, modifier = modifier, onClick = onClick, content = content)

@Composable
fun LiquidGlassIconButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) = GlassPill(
    selected = false,
    modifier = modifier.size(44.dp).semantics { this.contentDescription = contentDescription },
    onClick = onClick,
) { icon() }

@Composable
fun GlassNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerExtraLarge),
        elevated = true,
        opacity = 0.42f,
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 760f),
        label = "底栏图标胶囊按压缩放",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) -2f else 0f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 680f),
        label = "底栏图标位移",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .semantics { this.selected = selected }
            .padding(vertical = 5.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 32.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                    else Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(21.dp).graphicsLayer { translationY = lift },
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GlassActionSurface(
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassPill(selected = false, modifier = modifier, onClick = onClick) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = ((minHeight - 24.dp) / 2).coerceAtLeast(8.dp)),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

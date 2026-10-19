package com.jaydocoder.plateview.component.glass

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.luminance
import com.jaydocoder.plateview.PlateViewDimensions
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

private val LocalLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }

enum class GlassRenderMode {
    Static,
    BackdropBlur,
}

enum class GlassMaterial {
    Regular,
    Surface,
    Elevated,
    Toolbar,
    Input,
    Pill,
    Menu,
    Alert,
    Lens,
    Tinted,
}

enum class GlassSurfaceLevel {
    Page,
    Card,
    Block,
    Inset,
}

data class GlassStyle(
    val tintAlpha: Float,
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val haze: Float,
    val hazeBlur: Dp,
    val saturation: Float,
    val brightness: Float,
    val bevel: Dp,
    val rimStrength: Float,
    val rimWidth: Dp,
    val rimTail: Float,
    val rimFocus: Float,
    val rimBlur: Dp,
    val dispersion: Dp,
    val hairlineAlpha: Float,
    val highlightAlpha: Float,
    val shadowAlpha: Float,
    val innerShadowAlpha: Float,
    val borderAlpha: Float,
)

internal fun glassStyle(material: GlassMaterial, elevated: Boolean = false): GlassStyle = when (material) {
    GlassMaterial.Regular,
    GlassMaterial.Surface,
    -> GlassStyle(0.20f, 3.dp, 10.dp, 16.dp, 0.12f, 10.dp, 2.08f, 1f, 20.dp, 0.28f, 1.2.dp, 0.17f, 1f, 0.dp, 0.dp, 0.25f, 0.54f, 0.18f, 0.10f, 0.16f)
    GlassMaterial.Elevated -> GlassStyle(0.28f, 5.dp, 18.dp, 28.dp, 0.16f, 12.dp, 2.18f, 1.02f, 20.dp, 0.38f, 1.3.dp, 0.20f, 1f, 0.4.dp, 1.2.dp, 0.32f, 0.68f, 0.30f, 0.16f, 0.22f)
    GlassMaterial.Toolbar -> GlassStyle(0.34f, 10.dp, 14.dp, 22.dp, 0.14f, 14.dp, 2.12f, 1.01f, 18.dp, 0.34f, 1.1.dp, 0.18f, 1f, 0.5.dp, 0.8.dp, 0.28f, 0.58f, 0.24f, 0.14f, 0.20f)
    GlassMaterial.Input -> GlassStyle(0.24f, 4.dp, 12.dp, 18.dp, 0.18f, 12.dp, 2.10f, 1f, 18.dp, 0.32f, 1.2.dp, 0.18f, 1f, 0.2.dp, 0.dp, 0.28f, 0.58f, 0.18f, 0.10f, 0.18f)
    GlassMaterial.Pill -> GlassStyle(0.22f, 0.dp, 8.dp, 14.dp, 0.72f, 15.6.dp, 2.10f, 1f, 10.dp, 0.35f, 1.1.dp, 0.16f, 1f, 0.6.dp, 0.dp, 0.35f, 0.52f, 0.18f, 0.10f, 0.18f)
    GlassMaterial.Menu -> GlassStyle(0.42f, 12.dp, 18.dp, 24.dp, 0.10f, 12.dp, 1.90f, 1.02f, 20.dp, 0.25f, 1.2.dp, 0.15f, 1f, 0.6.dp, 1.dp, 0.20f, 0.48f, 0.30f, 0.18f, 0.22f)
    GlassMaterial.Alert -> GlassStyle(0.48f, 16.dp, 20.dp, 28.dp, 0.10f, 16.dp, 1.90f, 1.04f, 20.dp, 0.35f, 1.3.dp, 0.18f, 1f, 0.8.dp, 1.2.dp, 0.24f, 0.54f, 0.36f, 0.20f, 0.24f)
    GlassMaterial.Lens -> GlassStyle(0.06f, 0.dp, (-4.3).dp, 5.5.dp, 0f, 0.dp, 1f, 1f, 5.5.dp, 0.60f, 1.1.dp, 0.18f, 1f, 1.5.dp, 6.5.dp, 0.42f, 0.66f, 0.18f, 0.08f, 0.18f)
    GlassMaterial.Tinted -> GlassStyle(0.34f, 2.5.dp, 12.dp, 16.dp, 0.08f, 8.dp, 1.2f, 1f, 16.dp, 0.20f, 1.2.dp, 0.17f, 1f, 0.dp, 0.dp, 0.20f, 0.48f, 0.20f, 0.10f, 0.18f)
}.let { if (elevated && material == GlassMaterial.Surface) glassStyle(GlassMaterial.Elevated, true) else it }

internal fun supportsLiquidBackdrop(
    sdkInt: Int,
    manufacturer: String,
    brand: String,
): Boolean {
    if (sdkInt != Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
    val blocked = setOf("HONOR", "HUAWEI", "REALME", "OPPO", "VIVO")
    return manufacturer.trim().uppercase() !in blocked && brand.trim().uppercase() !in blocked
}

internal fun shouldUseLiquidBackdrop(context: Context): Boolean {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    return supportsLiquidBackdrop(
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
    ) && activityManager?.isLowRamDevice != true
}

@Composable
fun LiquidGlassScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = GlassBackdropHost(modifier, content)

@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = GlassBackdrop(modifier, content)

@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val renderMode = remember(context, view.isHardwareAccelerated) {
        if (shouldUseLiquidBackdrop(context) && view.isHardwareAccelerated) {
            GlassRenderMode.BackdropBlur
        } else {
            GlassRenderMode.Static
        }
    }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "低频背景光晕")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(18_000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "低频背景漂移",
    )
    val background = androidx.compose.ui.graphics.Brush.radialGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
            Color.Transparent,
        ),
        center = androidx.compose.ui.geometry.Offset(0.20f + drift * 0.12f, 0.12f),
        radius = 900f,
    )
    if (renderMode == GlassRenderMode.BackdropBlur) {
        val backdrop = rememberLayerBackdrop()
        Box(
            modifier = modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(Modifier.fillMaxSize().background(background))
            CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
                content()
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(Modifier.fillMaxSize().background(background))
            CompositionLocalProvider(LocalLiquidBackdrop provides null) {
                content()
            }
        }
    }
}

/** 页面根背景入口，保证整棵页面树只持有一个共享背景采样宿主。 */
@Composable
fun GlassPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = GlassBackdropHost(modifier, content)

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
    elevated: Boolean = false,
    color: Color = MaterialTheme.colorScheme.background,
    opacity: Float? = null,
    borderColor: Color? = null,
    borderOpacity: Float? = null,
    material: GlassMaterial = if (elevated) GlassMaterial.Elevated else GlassMaterial.Surface,
    level: GlassSurfaceLevel = if (elevated) GlassSurfaceLevel.Block else GlassSurfaceLevel.Card,
    pressedGlow: Float = 0f,
    showEdge: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalLiquidBackdrop.current
    val style = glassStyle(material, elevated)
    val levelAlpha = when (level) {
        GlassSurfaceLevel.Page -> 0.86f
        GlassSurfaceLevel.Card -> 1f
        GlassSurfaceLevel.Block -> 1.06f
        GlassSurfaceLevel.Inset -> 0.94f
    }
    val alpha = (opacity?.coerceIn(0f, 1f) ?: style.tintAlpha) * levelAlpha
    val tint = when {
        color == Color.Transparent -> Color.Transparent
        color == MaterialTheme.colorScheme.background || color == MaterialTheme.colorScheme.surface -> Color.White.copy(alpha = alpha)
        else -> color.copy(alpha = alpha)
    }
    // 玻璃材质在 Surface 外层绘制，保证裁剪、模糊和边缘高光使用同一形状。
    val materialModifier = modifier.clip(shape).let { clipped ->
        if (backdrop == null) {
            clipped.background(tint)
        } else {
            clipped.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(style.blurRadius.toPx())
                    lens(
                        refractionHeight = style.refractionHeight.toPx(),
                        refractionAmount = style.refractionAmount.toPx(),
                        chromaticAberration = material == GlassMaterial.Elevated || material == GlassMaterial.Alert,
                    )
                },
                highlight = { Highlight.Default.copy(alpha = if (showEdge) style.highlightAlpha else 0f) },
                shadow = { Shadow.Default.copy(alpha = if (showEdge) style.shadowAlpha else 0f) },
                innerShadow = { InnerShadow.Default.copy(alpha = if (showEdge) style.innerShadowAlpha else 0f) },
                onDrawSurface = { if (tint != Color.Transparent) drawRect(tint) },
            )
        }
    }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val edgedModifier = if (showEdge) {
        materialModifier.glassRim(
            shape = shape,
            style = style,
            dark = isDark,
            pressedGlow = pressedGlow.coerceIn(0f, 1f),
        )
    } else {
        materialModifier
    }
    Surface(
        modifier = edgedModifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
        border = if (showEdge) {
            BorderStroke(
                PlateViewDimensions.glassBorderWidth,
                (borderColor ?: MaterialTheme.colorScheme.primary).copy(
                    alpha = borderOpacity?.coerceIn(0f, 1f) ?: style.borderAlpha,
                ),
            )
        } else {
            null
        },
        tonalElevation = 0.dp,
    ) {
        Box(content = content)
    }
}

private fun Modifier.glassRim(
    shape: Shape,
    style: GlassStyle,
    dark: Boolean,
    pressedGlow: Float,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val edgeBrush = Brush.linearGradient(
        colors = if (dark) {
            listOf(
                Color.White.copy(alpha = style.rimStrength * 0.32f),
                Color.White.copy(alpha = style.rimStrength * 0.08f),
                Color.Black.copy(alpha = style.rimTail * 0.20f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = style.rimStrength * 0.76f),
                Color.White.copy(alpha = style.rimStrength * 0.20f),
                Color.Black.copy(alpha = style.rimTail * 0.10f),
            )
        },
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val hairlineBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = style.hairlineAlpha * if (dark) 0.42f else 0.82f),
            Color.Transparent,
            Color.Black.copy(alpha = style.hairlineAlpha * if (dark) 0.16f else 0.08f),
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val glowBrush = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = pressedGlow * 0.24f),
            Color.Transparent,
        ),
        center = Offset(size.width * 0.5f, size.height * 0.36f),
        radius = (size.maxDimension * 0.72f).coerceAtLeast(1f),
    )
    onDrawWithContent {
        drawContent()
        if (pressedGlow > 0f) drawGlassOutline(outline, glowBrush)
        drawGlassOutline(outline, edgeBrush, Stroke(width = style.rimWidth.toPx()))
        drawGlassOutline(outline, hairlineBrush, Stroke(width = 0.67.dp.toPx()))
    }
}

private fun DrawScope.drawGlassOutline(
    outline: Outline,
    brush: Brush,
    style: DrawStyle = androidx.compose.ui.graphics.drawscope.Fill,
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(brush = brush, style = style)
        is Outline.Rounded -> drawRoundRect(
            brush = brush,
            topLeft = Offset(outline.roundRect.left, outline.roundRect.top),
            size = androidx.compose.ui.geometry.Size(outline.roundRect.width, outline.roundRect.height),
            cornerRadius = outline.roundRect.topLeftCornerRadius,
            style = style,
        )
        is Outline.Generic -> drawPath(outline.path, brush = brush, style = style)
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
    inputModifier: Modifier = Modifier,
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
        material = GlassMaterial.Input,
        level = GlassSurfaceLevel.Inset,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().then(inputModifier),
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
            material = GlassMaterial.Alert,
            level = GlassSurfaceLevel.Block,
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
        color = if (transparent || selected) Color.Transparent else MaterialTheme.colorScheme.surface,
        opacity = if (transparent) 0f else if (selected) 0.14f else 0.22f,
        material = if (selected) GlassMaterial.Lens else GlassMaterial.Pill,
        pressedGlow = if (pressed) 1f else 0f,
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
        material = GlassMaterial.Pill,
        level = GlassSurfaceLevel.Block,
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
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .testTag("glass_navigation_item_$label")
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
                .clip(RoundedCornerShape(50))
                .testTag("glass_navigation_icon_container_$label"),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                GlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(50),
                    material = GlassMaterial.Lens,
                    level = GlassSurfaceLevel.Inset,
                    pressedGlow = if (pressed) 1f else 0f,
                ) {}
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .size(21.dp)
                    .testTag("glass_navigation_icon_$label"),
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

enum class GlassInteractiveState {
    Default,
    Pressed,
    Selected,
    Disabled,
    Focused,
}

@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    showEdge: Boolean = true,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        material = GlassMaterial.Toolbar,
        level = GlassSurfaceLevel.Block,
        color = Color.Transparent,
        showEdge = showEdge,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon?.invoke()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { title() }
            actions?.invoke(this)
        }
    }
}

@Composable
fun GlassMenuSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        material = GlassMaterial.Menu,
        level = GlassSurfaceLevel.Block,
        opacity = 0.72f,
        content = { Column(Modifier.padding(4.dp), content = content) },
    )
}

@Composable
fun GlassScrim(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    if (!visible) return
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
        content = content,
    )
}

@Composable
fun GlassSegmentedIndicator(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) = GlassPill(
    selected = selected,
    modifier = modifier,
    onClick = onClick,
    transparent = !selected,
    content = content,
)

@Composable
fun GlassSemanticBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) = GlassStatusBadge(text = text, color = color, modifier = modifier)

/** 详情页顶部身份区，统一承载主标题、辅助信息和状态徽标。 */
@Composable
fun GlassDetailHeader(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        material = GlassMaterial.Elevated,
        level = GlassSurfaceLevel.Block,
        content = content,
    )
}

/** 详情页主要信息分组；内部内容保持静态排版以避免长列表重复采样。 */
@Composable
fun GlassDetailBlock(
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerLarge),
        material = GlassMaterial.Elevated,
        level = GlassSurfaceLevel.Block,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.itemSpacing),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            content = content,
        )
    }
}

/** 字段、备注和来源等嵌套区域使用低对比度静态玻璃。 */
@Composable
fun GlassDetailInset(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        material = GlassMaterial.Regular,
        level = GlassSurfaceLevel.Inset,
        opacity = 0.16f,
        content = content,
    )
}

/** 统一语义状态徽标，颜色只来自当前主题的语义色。 */
@Composable
fun GlassStatusBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    contentColor: Color = color,
    prominent: Boolean = false,
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        material = GlassMaterial.Tinted,
        level = GlassSurfaceLevel.Inset,
        color = color,
        opacity = if (prominent) 0.52f else 0.20f,
        borderColor = color,
        borderOpacity = if (prominent) 0.62f else 0.34f,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** 附件和历史记录的静态承载层，不创建每行实时背景采样。 */
@Composable
fun GlassAttachmentSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        material = GlassMaterial.Regular,
        level = GlassSurfaceLevel.Inset,
        opacity = 0.18f,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.compactSpacing),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.compactSpacing),
            content = content,
        )
    }
}

@Composable
fun GlassHistoryItem(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PlateViewDimensions.cornerMedium),
        material = GlassMaterial.Regular,
        level = GlassSurfaceLevel.Inset,
        opacity = 0.13f,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(PlateViewDimensions.compactSpacing),
            verticalArrangement = Arrangement.spacedBy(PlateViewDimensions.tinySpacing),
            content = content,
        )
    }
}

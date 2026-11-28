package com.cipher.virtual.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws dual directional Soft UI (Neumorphic) shadows behind the composable:
 * a soft top-left highlight bloom, a soft bottom-right drop shadow, the base surface fill,
 * and a crisp diagonal bevel rim.
 */
fun Modifier.neuExtruded(
    cornerRadius: Dp = 22.dp,
    elevation: Dp = 6.dp,
    containerColor: Color? = null,
    accentTint: Color? = null,
    isCircular: Boolean = false
): Modifier = composed {
    val palette = LocalNeuPalette.current
    val fill = containerColor ?: palette.raisedSurface
    val topLeftGlow = accentTint?.copy(alpha = if (palette.isDark) 0.22f else 0.18f)
        ?: palette.highlightShadow.copy(alpha = if (palette.isDark) 0.72f else 0.90f)
    val bottomRightShadow = palette.darkShadow.copy(alpha = if (palette.isDark) 0.85f else 0.78f)
    val bevelTopLeft = accentTint?.copy(alpha = 0.42f)
        ?: palette.bevelHighlight.copy(alpha = if (palette.isDark) 0.48f else 0.85f)
    val bevelBottomRight = palette.bevelShadow.copy(alpha = if (palette.isDark) 0.52f else 0.45f)

    this.drawWithCache {
        val offsetPx = elevation.toPx()
        val blurPx = (offsetPx * 1.85f).coerceAtLeast(1f)
        val radiusPx = if (isCircular) minOf(size.width, size.height) / 2f else cornerRadius.toPx()

        val lightPaint = Paint().apply {
            isAntiAlias = true
            color = topLeftGlow
        }.also { p ->
            p.asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }

        val darkPaint = Paint().apply {
            isAntiAlias = true
            color = bottomRightShadow
        }.also { p ->
            p.asFrameworkPaint().maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }

        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = fill
        }

        val bevelBrush = Brush.linearGradient(
            colors = listOf(bevelTopLeft, bevelBottomRight),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )

        onDrawBehind {
            if (offsetPx > 0.25f && size.width > 0f && size.height > 0f) {
                drawIntoCanvas { canvas ->
                    canvas.drawRoundRect(
                        left = -offsetPx,
                        top = -offsetPx,
                        right = size.width - offsetPx,
                        bottom = size.height - offsetPx,
                        radiusX = radiusPx,
                        radiusY = radiusPx,
                        paint = lightPaint
                    )
                    canvas.drawRoundRect(
                        left = offsetPx,
                        top = offsetPx,
                        right = size.width + offsetPx,
                        bottom = size.height + offsetPx,
                        radiusX = radiusPx,
                        radiusY = radiusPx,
                        paint = darkPaint
                    )
                    canvas.drawRoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        radiusX = radiusPx,
                        radiusY = radiusPx,
                        paint = fillPaint
                    )
                }
            } else {
                drawRoundRect(
                    color = fill,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            }
            drawRoundRect(
                brush = bevelBrush,
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Draws a concave / debossed Soft UI well with inner top-left dark shading and
 * bottom-right specular rim.
 */
fun Modifier.neuPressed(
    cornerRadius: Dp = 16.dp,
    containerColor: Color? = null,
    depth: Dp = 3.dp
): Modifier = composed {
    val palette = LocalNeuPalette.current
    val fill = containerColor ?: palette.pressedSurface
    val innerShadow = palette.darkShadow.copy(alpha = if (palette.isDark) 0.78f else 0.58f)
    val innerHighlight = palette.bevelHighlight.copy(alpha = if (palette.isDark) 0.34f else 0.85f)

    this
        .clip(RoundedCornerShape(cornerRadius))
        .drawWithCache {
            val rPx = cornerRadius.toPx()
            val dPx = depth.toPx().coerceAtLeast(1.dp.toPx())
            val innerBevel = Brush.linearGradient(
                colors = listOf(innerShadow, Color.Transparent, innerHighlight),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val topGradient = Brush.verticalGradient(
                colors = listOf(innerShadow.copy(alpha = innerShadow.alpha * 0.45f), Color.Transparent),
                startY = 0f,
                endY = (dPx * 3.5f).coerceAtMost(size.height * 0.45f)
            )
            onDrawBehind {
                drawRoundRect(
                    color = fill,
                    cornerRadius = CornerRadius(rPx, rPx)
                )
                drawRoundRect(
                    brush = topGradient,
                    cornerRadius = CornerRadius(rPx, rPx)
                )
                drawRoundRect(
                    brush = innerBevel,
                    cornerRadius = CornerRadius(rPx, rPx),
                    style = Stroke(width = 1.25.dp.toPx())
                )
            }
        }
}

@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 6.dp,
    containerColor: Color? = null,
    accentTint: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .neuExtruded(
                cornerRadius = cornerRadius,
                elevation = elevation,
                containerColor = containerColor,
                accentTint = accentTint
            )
            .clip(shape),
        content = content
    )
}

@Composable
fun NeuWell(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    containerColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.neuPressed(
            cornerRadius = cornerRadius,
            containerColor = containerColor
        ),
        content = content
    )
}

@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    cornerRadius: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalNeuPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 1.dp
            pressed -> 2.dp
            else -> 5.dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "neu_btn_elev"
    )
    val resolvedContainer = if (enabled) containerColor else palette.pressedSurface
    val resolvedContent = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .neuExtruded(
                cornerRadius = cornerRadius,
                elevation = elevation,
                containerColor = resolvedContainer,
                accentTint = if (enabled) containerColor else null
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = resolvedContent),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContent) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    content = content
                )
            }
        }
    }
}

@Composable
fun NeuTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    cornerRadius: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalNeuPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 1.dp
            pressed -> 1.5.dp
            else -> 4.5.dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "neu_tonal_elev"
    )
    val fill = if (enabled) palette.raisedSurface else palette.pressedSurface
    val resolvedContent = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .neuExtruded(
                cornerRadius = cornerRadius,
                elevation = elevation,
                containerColor = fill,
                accentTint = accentColor
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContent) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    content = content
                )
            }
        }
    }
}

@Composable
fun NeuIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit
) {
    val palette = LocalNeuPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = if (!enabled || pressed) 1.5.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "neu_icon_elev"
    )
    val fill = containerColor ?: palette.raisedSurface

    Box(
        modifier = modifier
            .size(size)
            .neuExtruded(
                cornerRadius = size / 2f,
                elevation = elevation,
                containerColor = fill,
                isCircular = true
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun NeuChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val palette = LocalNeuPalette.current
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val chipModifier = if (selected) {
        modifier
            .defaultMinSize(minHeight = 38.dp)
            .neuPressed(
                cornerRadius = 14.dp,
                containerColor = selectedContainerColor.copy(alpha = if (palette.isDark) 0.72f else 0.85f)
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.55f),
                shape = shape
            )
    } else {
        modifier
            .defaultMinSize(minHeight = 38.dp)
            .neuExtruded(
                cornerRadius = 14.dp,
                elevation = 3.5.dp,
                containerColor = palette.raisedSurface
            )
            .clip(shape)
    }

    val contentColor = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = chipModifier
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                    }
                    label()
                }
            }
        }
    }
}

@Composable
fun neuTextFieldColors(accentColor: Color = MaterialTheme.colorScheme.primary): TextFieldColors {
    val palette = LocalNeuPalette.current
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = palette.pressedSurface,
        unfocusedContainerColor = palette.pressedSurface,
        focusedBorderColor = accentColor.copy(alpha = 0.75f),
        unfocusedBorderColor = palette.bevelHighlight.copy(alpha = if (palette.isDark) 0.25f else 0.7f),
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = accentColor,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = accentColor
    )
}

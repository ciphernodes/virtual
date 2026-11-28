package com.cipher.virtual.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cipher.virtual.ui.theme.LocalNeuPalette
import com.cipher.virtual.ui.theme.NeuWell
import com.cipher.virtual.ui.theme.neuPressed
import java.util.Locale

@Composable
fun AnimatedPulseDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun LiveSparklineChart(
    samples: List<Float>,
    lineColor: Color,
    unitSuffix: String,
    maxValueHint: Float = 100f,
    chartHeight: Dp = 74.dp,
    modifier: Modifier = Modifier
) {
    val normalizedSamples = if (samples.size >= 2) {
        samples
    } else {
        val seed = samples.firstOrNull() ?: 0f
        listOf(seed, seed)
    }
    val peak = (normalizedSamples.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val scaleMax = maxOf(maxValueHint, peak * 1.15f).coerceAtLeast(1f)
    val latest = normalizedSamples.last()
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "Peak %.1f%s", peak, unitSuffix),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "Now %.1f%s", latest, unitSuffix),
                style = MaterialTheme.typography.labelMedium,
                color = lineColor
            )
        }

        NeuWell(
            cornerRadius = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                    val y = h * fraction
                    drawLine(
                        color = gridColor.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash
                    )
                }

                val count = normalizedSamples.size
                val stepX = w / (count - 1).coerceAtLeast(1)
                val points = normalizedSamples.mapIndexed { index, value ->
                    val ratio = (value / scaleMax).coerceIn(0f, 1f)
                    val x = index * stepX
                    val y = h - (ratio * (h - 8.dp.toPx())) - 4.dp.toPx()
                    Offset(x, y)
                }

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(points.last().x, h)
                    lineTo(points.first().x, h)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.34f),
                            lineColor.copy(alpha = 0.02f)
                        )
                    )
                )

                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                val lastPoint = points.last()
                drawCircle(
                    color = lineColor.copy(alpha = 0.25f),
                    radius = 6.dp.toPx(),
                    center = lastPoint
                )
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = lastPoint
                )
            }
        }
    }
}

@Composable
fun MemoryAllocationBar(
    appHeapMb: Long,
    nativeHeapMb: Long,
    systemUsedMb: Long,
    systemTotalMb: Long,
    modifier: Modifier = Modifier
) {
    val total = systemTotalMb.coerceAtLeast(1L).toFloat()
    val appFractionTarget = (appHeapMb / total).coerceIn(0f, 1f)
    val nativeFractionTarget = (nativeHeapMb / total).coerceIn(0f, 1f)
    val systemOtherTarget = ((systemUsedMb - appHeapMb - nativeHeapMb).coerceAtLeast(0L) / total)
        .coerceIn(0f, 1f)

    val appFraction by animateFloatAsState(appFractionTarget, spring(), label = "app_mem")
    val nativeFraction by animateFloatAsState(nativeFractionTarget, spring(), label = "native_mem")
    val otherFraction by animateFloatAsState(systemOtherTarget, spring(), label = "other_mem")

    val palette = LocalNeuPalette.current
    val appColor = MaterialTheme.colorScheme.primary
    val nativeColor = MaterialTheme.colorScheme.secondary
    val otherColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
    val trackColor = palette.pressedSurface

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .neuPressed(cornerRadius = 6.dp, containerColor = trackColor, depth = 2.dp)
        ) {
            val w = size.width
            val h = size.height
            val radius = CornerRadius(h / 2f, h / 2f)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = radius
            )

            var cursorX = 0f
            val segments = listOf(
                otherFraction to otherColor,
                appFraction to appColor,
                nativeFraction to nativeColor
            )
            segments.forEach { (fraction, color) ->
                val segWidth = (fraction * w).coerceAtMost(w - cursorX)
                if (segWidth > 1f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(cursorX, 0f),
                        size = Size(segWidth, h),
                        cornerRadius = radius
                    )
                    cursorX += segWidth
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendChip(color = otherColor, label = "OS")
            LegendChip(color = appColor, label = "App Heap ${appHeapMb}MB")
            LegendChip(color = nativeColor, label = "Native ${nativeHeapMb}MB")
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

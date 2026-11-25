package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a Neomorphic (Soft UI) extruded surface effect with dual light and dark soft shadows.
 */
fun Modifier.neomorphic(
    color: Color,
    lightShadowColor: Color,
    darkShadowColor: Color,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 6.dp,
    isPressed: Boolean = false
): Modifier = this.composed {
    drawBehind {
        val cornerRadiusPx = cornerRadius.toPx()
        val elevationPx = elevation.toPx()

        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint()
            paint.isAntiAlias = true

            if (!isPressed) {
                // Outer dark shadow (bottom-right)
                paint.color = darkShadowColor.toArgb()
                paint.setShadowLayer(elevationPx * 1.2f, elevationPx * 0.8f, elevationPx * 0.8f, darkShadowColor.toArgb())
                canvas.nativeCanvas.drawRoundRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    paint
                )

                // Outer light highlight (top-left)
                paint.color = lightShadowColor.toArgb()
                paint.setShadowLayer(elevationPx * 1.2f, -elevationPx * 0.8f, -elevationPx * 0.8f, lightShadowColor.toArgb())
                canvas.nativeCanvas.drawRoundRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    paint
                )
            } else {
                // Inset / pressed inner shadow effect
                paint.color = darkShadowColor.toArgb()
                paint.setShadowLayer(elevationPx * 0.6f, elevationPx * 0.4f, elevationPx * 0.4f, darkShadowColor.toArgb())
                canvas.nativeCanvas.drawRoundRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    cornerRadiusPx,
                    cornerRadiusPx,
                    paint
                )
            }
        }
    }.background(color, shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius))
}

/**
 * Inset neomorphic modifier for recessed wells, search bars, and inner sliders.
 */
fun Modifier.neomorphicInset(
    color: Color,
    lightShadowColor: Color,
    darkShadowColor: Color,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 4.dp
): Modifier = this.composed {
    drawBehind {
        val cornerRadiusPx = cornerRadius.toPx()
        val elevationPx = elevation.toPx()

        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint()
            paint.isAntiAlias = true

            // Inner top-left dark shadow
            paint.color = darkShadowColor.toArgb()
            paint.setShadowLayer(elevationPx, -elevationPx * 0.5f, -elevationPx * 0.5f, darkShadowColor.toArgb())
            canvas.nativeCanvas.drawRoundRect(
                0f,
                0f,
                size.width,
                size.height,
                cornerRadiusPx,
                cornerRadiusPx,
                paint
            )

            // Inner bottom-right light highlight
            paint.color = lightShadowColor.toArgb()
            paint.setShadowLayer(elevationPx, elevationPx * 0.5f, elevationPx * 0.5f, lightShadowColor.toArgb())
            canvas.nativeCanvas.drawRoundRect(
                0f,
                0f,
                size.width,
                size.height,
                cornerRadiusPx,
                cornerRadiusPx,
                paint
            )
        }
    }.background(color, shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius))
}

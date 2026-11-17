package com.nightvision.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Crisp, non-interactive tactical reticle overlay drawn on a vector Canvas.
 * Renders subtle corner brackets, center crosshair gap, and rangefinder ticks.
 */
@Composable
fun TacticalReticle(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val primaryAlpha = 0.62f
        val subtleAlpha = 0.28f
        val thinStroke = 1.dp.toPx()
        val crispStroke = 1.35.dp.toPx()

        // Center dot
        drawCircle(
            color = accentColor.copy(alpha = 0.85f),
            radius = 2.dp.toPx(),
            center = center
        )

        // Center crosshair lines with inner gap
        val innerGap = 14.dp.toPx()
        val armLength = 28.dp.toPx()

        // Horizontal arms
        drawLine(
            color = accentColor.copy(alpha = primaryAlpha),
            start = Offset(center.x - innerGap - armLength, center.y),
            end = Offset(center.x - innerGap, center.y),
            strokeWidth = crispStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = accentColor.copy(alpha = primaryAlpha),
            start = Offset(center.x + innerGap, center.y),
            end = Offset(center.x + innerGap + armLength, center.y),
            strokeWidth = crispStroke,
            cap = StrokeCap.Round
        )

        // Vertical arms
        drawLine(
            color = accentColor.copy(alpha = primaryAlpha),
            start = Offset(center.x, center.y - innerGap - armLength),
            end = Offset(center.x, center.y - innerGap),
            strokeWidth = crispStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = accentColor.copy(alpha = primaryAlpha),
            start = Offset(center.x, center.y + innerGap),
            end = Offset(center.x, center.y + innerGap + armLength),
            strokeWidth = crispStroke,
            cap = StrokeCap.Round
        )

        // Rangefinder mil-ticks along horizontal and vertical axes
        val tickHalf = 3.5.dp.toPx()
        val tickSpacing = 12.dp.toPx()
        for (i in 1..3) {
            val offset = innerGap + armLength + i * tickSpacing
            val tickAlpha = if (i == 2) primaryAlpha * 0.85f else subtleAlpha
            val len = if (i == 2) tickHalf * 1.4f else tickHalf

            // Left & right ticks
            drawLine(
                color = accentColor.copy(alpha = tickAlpha),
                start = Offset(center.x - offset, center.y - len),
                end = Offset(center.x - offset, center.y + len),
                strokeWidth = thinStroke
            )
            drawLine(
                color = accentColor.copy(alpha = tickAlpha),
                start = Offset(center.x + offset, center.y - len),
                end = Offset(center.x + offset, center.y + len),
                strokeWidth = thinStroke
            )

            // Top & bottom ticks
            drawLine(
                color = accentColor.copy(alpha = tickAlpha),
                start = Offset(center.x - len, center.y - offset),
                end = Offset(center.x + len, center.y - offset),
                strokeWidth = thinStroke
            )
            drawLine(
                color = accentColor.copy(alpha = tickAlpha),
                start = Offset(center.x - len, center.y + offset),
                end = Offset(center.x + len, center.y + offset),
                strokeWidth = thinStroke
            )
        }

        // Subtle outer framing brackets around center
        val bracketHalfWidth = (size.minDimension * 0.27f)
        val bracketLen = 18.dp.toPx()
        val bracketColor = accentColor.copy(alpha = subtleAlpha)
        drawCornerBrackets(
            left = center.x - bracketHalfWidth,
            top = center.y - bracketHalfWidth,
            right = center.x + bracketHalfWidth,
            bottom = center.y + bracketHalfWidth,
            cornerLength = bracketLen,
            color = bracketColor,
            strokeWidth = thinStroke
        )
    }
}

private fun DrawScope.drawCornerBrackets(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cornerLength: Float,
    color: Color,
    strokeWidth: Float
) {
    // Top-left
    drawLine(color, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
    drawLine(color, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)

    // Top-right
    drawLine(color, Offset(right - cornerLength, top), Offset(right, top), strokeWidth)
    drawLine(color, Offset(right, top), Offset(right, top + cornerLength), strokeWidth)

    // Bottom-left
    drawLine(color, Offset(left, bottom - cornerLength), Offset(left, bottom), strokeWidth)
    drawLine(color, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth)

    // Bottom-right
    drawLine(color, Offset(right - cornerLength, bottom), Offset(right, bottom), strokeWidth)
    drawLine(color, Offset(right, bottom - cornerLength), Offset(right, bottom), strokeWidth)
}

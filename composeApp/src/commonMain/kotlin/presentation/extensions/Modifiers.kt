package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.plus
import kotlin.math.roundToInt


fun Modifier.drawSate(
    percentage: Float = 0.125f,
    isInLine: Boolean = true,
    alignment: Alignment = Alignment.BottomEnd,
    enable: Boolean = true,
    onDraw: ContentDrawScope.(Float, Offset) -> Unit
) = if (enable)  this.drawWithContent {
    val borderRadius = size.minDimension * percentage
    val borderTopStart = if (isInLine) Offset(-borderRadius, -borderRadius) else Offset.Zero
    val borderDiameter = borderRadius * 2
    val borderOffset = borderTopStart.plus(
        alignment.align(
            IntSize(borderDiameter.roundToInt(), borderDiameter.roundToInt()),
            space = IntSize((size.width+borderDiameter).toInt(), (size.height+borderDiameter).toInt()),
            layoutDirection = layoutDirection
        )
    )
    onDraw(borderRadius,borderOffset)
} else this

fun Modifier.drawSateCircle(
    color: Color,
    percentage: Float = 0.125f,
    borderWidth: Dp = 16.dp,
    isInLine: Boolean = true,
    alignment: Alignment = Alignment.BottomEnd,
    enable: Boolean = true,
    onDraw: ContentDrawScope.(Float, Offset) -> Unit = { borderRadius: Float, borderOffset: Offset ->
        val radius = (size.minDimension - borderWidth.toPx()) * percentage
        this.drawContent()
        drawCircle(Color.White, borderRadius, borderOffset)
        drawCircle(color, radius, borderOffset)
    }
) = drawSate(
    percentage = percentage,
    isInLine = isInLine,
    alignment = alignment,
    enable = enable,
    onDraw = onDraw
)

fun Modifier.enableIf(enable: Boolean = true, effect: Modifier.() -> Modifier) = if (enable) effect() else this
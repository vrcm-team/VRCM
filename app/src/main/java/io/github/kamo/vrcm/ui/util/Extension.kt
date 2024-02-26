package io.github.kamo.vrcm.ui.util

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.plus
import java.util.Locale
import kotlin.math.roundToInt


fun Modifier.sateCircle(
    color: Color,
    percentage: Float = 0.125f,
    borderWidth: Dp = 16.dp,
    isInLine: Boolean = true,
    alignment: Alignment = Alignment.BottomEnd,
) = this.drawWithContent {
    val radius = (size.minDimension - borderWidth.toPx()) * percentage
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
    this.drawContent()
    drawCircle(Color.White, borderRadius, borderOffset)
    drawCircle(color, radius, borderOffset)
}

fun String.capitalizeFirst(locale: Locale = Locale.ROOT) = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }




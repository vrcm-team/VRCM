package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val SliderThumbSize = 28.dp
private val SliderTrackHeight = 4.dp

/**
 * 滑块（HIG Sliders）：4 dp 细轨道，已选一侧着色；28 dp 白色圆形滑块带投影。
 * 点轨道直接跳到该处，拖动连续变化；[steps] > 0 时吸附到等分刻度。
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun valueAt(x: Float, width: Float, thumbPx: Float): Float {
        val usable = (width - thumbPx).coerceAtLeast(1f)
        var f = ((x - thumbPx / 2f) / usable).coerceIn(0f, 1f)
        if (rtl) f = 1f - f
        if (steps > 0) f = (f * (steps + 1)).roundToInt() / (steps + 1).toFloat()
        return valueRange.start + f * span
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                if (!enabled) disabled()
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                setProgress { target ->
                    val coerced = target.coerceIn(valueRange)
                    if (coerced == value) false else { currentOnValueChange(coerced); currentOnFinished?.invoke(); true }
                }
            }
            .pointerInput(enabled, valueRange, steps, rtl) {
                if (!enabled) return@pointerInput
                detectTapGestures { position ->
                    currentOnValueChange(valueAt(position.x, size.width.toFloat(), SliderThumbSize.toPx()))
                    currentOnFinished?.invoke()
                }
            }
            .pointerInput(enabled, valueRange, steps, rtl) {
                if (!enabled) return@pointerInput
                var dragX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        dragX = start.x
                        currentOnValueChange(valueAt(dragX, size.width.toFloat(), SliderThumbSize.toPx()))
                    },
                    onDragEnd = { currentOnFinished?.invoke() },
                    onDragCancel = { currentOnFinished?.invoke() },
                ) { change, delta ->
                    change.consume()
                    dragX += delta
                    currentOnValueChange(valueAt(dragX, size.width.toFloat(), SliderThumbSize.toPx()))
                }
            },
    ) {
        val thumbPx = SliderThumbSize.toPx()
        val trackHeight = SliderTrackHeight.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val radius = CornerRadius(trackHeight / 2f)
        val visualFraction = if (rtl) 1f - fraction else fraction
        val thumbCenterX = thumbPx / 2f + (size.width - thumbPx) * visualFraction
        drawRoundRect(c.fill, topLeft = Offset(0f, trackTop), size = Size(size.width, trackHeight), cornerRadius = radius)
        if (rtl) {
            drawRoundRect(c.tint, topLeft = Offset(thumbCenterX, trackTop), size = Size(size.width - thumbCenterX, trackHeight), cornerRadius = radius)
        } else {
            drawRoundRect(c.tint, topLeft = Offset(0f, trackTop), size = Size(thumbCenterX, trackHeight), cornerRadius = radius)
        }
        val center = Offset(thumbCenterX, size.height / 2f)
        // 滑块投影：两层半透明圆近似 iOS 的柔和阴影
        drawCircle(Color.Black.copy(alpha = 0.10f), radius = thumbPx / 2f + 1.dp.toPx(), center = center.copy(y = center.y + 2.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.06f), radius = thumbPx / 2f + 0.5.dp.toPx(), center = center)
        drawCircle(Color.White, radius = thumbPx / 2f, center = center)
    }
}

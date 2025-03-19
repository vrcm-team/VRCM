package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.plus
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlin.math.roundToInt


fun Modifier.drawSate(
    percentage: Float = 0.125f,
    isInLine: Boolean = true,
    alignment: Alignment = Alignment.BottomEnd,
    enable: Boolean = true,
    onDraw: ContentDrawScope.(Float, Offset) -> Unit,
) = if (enable) this.drawWithContent {
    val borderRadius = size.maxDimension * percentage
    val borderDiameter = borderRadius * 2
    val borderTopStart = if (isInLine) Offset(-borderRadius, -borderRadius) else Offset.Zero
    val borderOffset = borderTopStart.plus(
        alignment.align(
            IntSize(borderDiameter.roundToInt(), borderDiameter.roundToInt()),
            space = IntSize(
                (size.width + borderDiameter).toInt(),
                (size.height + borderDiameter).toInt()
            ),
            layoutDirection = layoutDirection
        )
    )
    onDraw(borderRadius, borderOffset)
} else this

fun Modifier.drawSateCircle(
    color: Color,
    percentage: Float = 0.125f,
    borderWidth: Dp = 16.dp,
    isInLine: Boolean = true,
    alignment: Alignment = Alignment.BottomEnd,
    enable: Boolean = true,
    onDraw: ContentDrawScope.(Float, Offset) -> Unit = { borderRadius: Float, borderOffset: Offset ->
        val radius = (size.maxDimension - borderWidth.toPx()) * percentage
        this.drawContent()
        drawCircle(Color.White, borderRadius, borderOffset)
        drawCircle(color, radius, borderOffset)
    },
) = drawSate(
    percentage = percentage,
    isInLine = isInLine,
    alignment = alignment,
    enable = enable,
    onDraw = onDraw
)

@Composable
inline fun Modifier.enableIf(enable: Boolean = true, effect: @Composable Modifier.() -> Modifier) =
    if (enable) effect() else this

/**
 * 侧滑返回
 */
fun Modifier.slideBack(
    threshold: Float = 100.dp.value,
    orientation: Orientation = Orientation.Horizontal,
) = this.composed {
    val navigator = LocalNavigator.currentOrThrow
    val onBackHook = LocalOnBackHook.current.value
    // 添加一个标志变量，用于确保在一次拖动手势中只pop一次
    val hasPopped = remember { mutableStateOf(false) }
    
    draggable(
        state = rememberDraggableState {
            if (navigator.canPop && it > threshold && !hasPopped.value && onBackHook()) {
                navigator.pop()
                hasPopped.value = true
            }
        },
        orientation = orientation,
        onDragStarted = {
            // 当新的拖动手势开始时，重置标志
            hasPopped.value = false
        },
        onDragStopped = {
            // 当拖动手势结束时，重置标志（可选，取决于需求）
            hasPopped.value = false
        }
    )
}

/**
 * 下滑返回
 */
fun Modifier.glideBack(
    threshold: Float = 30.dp.value,
    orientation: Orientation = Orientation.Vertical,
    onBack: () -> Unit,
) = this.composed {
    draggable(rememberDraggableState {
        if (it > threshold) onBack()
    }, orientation)
}


/**
 * 去除点击水波纹效果
  */
 @Composable
fun Modifier.simpleClickable(
    onClick: () -> Unit,
) = this.clickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() },
    onClick = onClick
)


val LocalOnBackHook = compositionLocalOf { mutableStateOf({ true }) }
package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 帮助标签（HIG Tooltips）：指针悬停片刻、或触摸长按时，在控件上方浮出一小段说明；移开 / 稍候自动消失。
 * 不拦截控件自身的点击。
 */
@Composable
fun AppTooltipBox(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis

    LaunchedEffect(hovered) {
        if (hovered) {
            delay(HoverDelayMillis)
            visible = true
        } else {
            visible = false
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            delay(TooltipDurationMillis)
            visible = false
        }
    }

    Box(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.none { it.type == PointerType.Mouse }) continue
                        when (event.type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit, PointerEventType.Press -> hovered = false
                        }
                    }
                }
            }
            .pointerInput(longPressTimeout) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.type == PointerType.Mouse) return@awaitEachGesture
                    // 按住超过长按时长还没抬手 → 显示；期间抬手就是普通点击，交给控件自己
                    val up = withTimeoutOrNull(longPressTimeout) { waitForUpOrCancellation(PointerEventPass.Initial) }
                    if (up == null) visible = true
                }
            },
    ) {
        content()
        if (visible) {
            val density = LocalDensity.current
            val provider = remember(density) { TooltipPositionProvider(with(density) { 6.dp.roundToPx() }) }
            Popup(popupPositionProvider = provider, onDismissRequest = { visible = false }) {
                val c = AppTheme.colors.elevated()
                CompositionLocalProvider(LocalAppColors provides c) {
                    Box(
                        Modifier
                            .padding(6.dp)
                            .shadow(6.dp, AppShapes.xs, clip = false)
                            .clip(AppShapes.xs)
                            .background(c.secondarySystemBackground)
                            .border(0.5.dp, c.separator, AppShapes.xs)
                            .widthIn(max = 240.dp)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        ProvideContentColor(c.label, AppTheme.type.caption1, tooltip)
                    }
                }
            }
        }
    }
}

private const val HoverDelayMillis = 600L
private const val TooltipDurationMillis = 2500L

/** 居中放在锚点上方，放不下就放到下方；横向夹在窗口内。 */
private class TooltipPositionProvider(private val spacing: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - spacing - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom + spacing
        return IntOffset(x, y)
    }
}

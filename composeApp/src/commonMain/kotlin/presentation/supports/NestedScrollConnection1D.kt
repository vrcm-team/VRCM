package io.github.vrcmteam.vrcm.presentation.supports

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * 一维的嵌套滑动连接器
 * @author kamosama
 */
private class NestedScrollConnection1D(
    private val orientation: Orientation,
    private val consumer: (Float) -> Float
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val offset = if (orientation == Orientation.Vertical) available.y else available.x
        val consumeOffset = consumer(offset)
        return if (consumeOffset != 0f) {
            if (orientation == Orientation.Vertical)
                Offset(x = 0f, consumeOffset)
            else Offset(consumeOffset, y = 0f)
        } else {
            Offset.Zero
        }
    }
}

/**
 * 阀值嵌套滑动连接器
 * 没到阀值时不消费，全交给滑动消费函数消费
 * @param reachThreshold 判断是否达到阀值的函数, 返回true时消费
 * @param orientation 滑动方向
 * @param consumer 滑动消费函数
 */
@Composable
fun thresholdNestedScrollConnection(
    reachThreshold: () -> Boolean,
    orientation: Orientation = Orientation.Vertical,
    consumer: (Float) -> Unit
): NestedScrollConnection = remember {
    NestedScrollConnection1D(orientation) {
        if (reachThreshold()) {
            consumer(it)
            it
        } else {
            0f
        }
    }
}

/**
 * 同时消费嵌套滑动连接器
 * @param orientation 滑动方向
 * @param consumer 滑动消费函数
 */
@Composable
fun commonNestedScrollConnection(
    orientation: Orientation = Orientation.Vertical,
    consumer: (Float) -> Unit
): NestedScrollConnection = remember {
    NestedScrollConnection1D(orientation) {
        consumer(it)
        0f
    }
}

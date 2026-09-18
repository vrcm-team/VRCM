package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 下拉刷新的拉动状态：内容当前被拉下的距离，以及相对触发阈值的比例。 */
@Stable
class AppPullToRefreshState internal constructor() {
    internal val offset = Animatable(0f)
    internal var thresholdPx = 1f

    /** 已拉下的距离 / 触发阈值；超过 1 表示松手即刷新。 */
    val distanceFraction: Float get() = offset.value / thresholdPx

    val isAnimating: Boolean get() = offset.isRunning
}

@Composable
fun rememberAppPullToRefreshState(): AppPullToRefreshState = remember { AppPullToRefreshState() }

private val RefreshThreshold = 72.dp
private val RefreshingOffset = 52.dp

/**
 * 下拉刷新（HIG Refresh content controls）：内容整体被拉下来，活动指示器出现在露出的空隙里，而不是悬在内容上方的浮标。
 * 拉过阈值松手触发 [onRefresh]；刷新期间内容停在指示器下方，结束后弹回。
 * [indicatorOffset] 是指示器的额外顶部偏移（内容从半透明顶栏下面开始时用）。
 */
@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: AppPullToRefreshState = rememberAppPullToRefreshState(),
    indicatorOffset: Dp = 0.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { RefreshThreshold.toPx() }
    val refreshingPx = with(density) { RefreshingOffset.toPx() }
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    state.thresholdPx = thresholdPx

    LaunchedEffect(isRefreshing) {
        state.offset.animateTo(if (isRefreshing) refreshingPx else 0f, spring(dampingRatio = 0.9f, stiffness = 300f))
    }

    val connection = remember(state, thresholdPx, refreshingPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 已经拉下来时往回推：先把拉下的距离还回去，再让列表滚动
                if (source != NestedScrollSource.UserInput || available.y >= 0f || state.offset.value <= 0f) return Offset.Zero
                val target = (state.offset.value + available.y).coerceAtLeast(0f)
                val consumed = target - state.offset.value
                scope.launch { state.offset.snapTo(target) }
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                // 橡皮筋阻尼：拉得越远越费力
                val resistance = 0.5f / (1f + state.offset.value / (thresholdPx * 2f))
                scope.launch { state.offset.snapTo(state.offset.value + available.y * resistance) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val pulled = state.offset.value
                if (pulled <= 0f) return Velocity.Zero
                if (pulled >= thresholdPx && !currentIsRefreshing) {
                    currentOnRefresh()
                    state.offset.animateTo(refreshingPx, spring(dampingRatio = 0.9f, stiffness = 300f))
                } else {
                    state.offset.animateTo(if (currentIsRefreshing) refreshingPx else 0f, spring(dampingRatio = 0.9f, stiffness = 300f))
                }
                // 松手时的速度留给回弹，不再传给列表
                return available
            }
        }
    }

    Box(modifier.clipToBounds().nestedScroll(connection), contentAlignment = contentAlignment) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (indicatorOffset.toPx() + (state.offset.value - 28.dp.toPx()) / 2f).roundToInt()) }
                .graphicsLayer {
                    val progress = (state.offset.value / refreshingPx).coerceIn(0f, 1f)
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = 0.6f + 0.4f * progress
                },
        ) {
            if (state.offset.value > 0f) AppActivityIndicator(Modifier.size(28.dp))
        }
        // 内容决定容器的尺寸（调用方可以不给尺寸），所以不能用 matchParentSize
        Box(
            Modifier.offset { IntOffset(0, state.offset.value.roundToInt()) },
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

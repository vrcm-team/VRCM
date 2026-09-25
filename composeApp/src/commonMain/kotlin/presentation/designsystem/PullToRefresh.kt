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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 下拉刷新的拉动状态：内容当前被拉下的距离，以及相对触发阈值的比例。 */
@Stable
class AppPullToRefreshState internal constructor() {
    internal val offset = Animatable(0f)
    internal var thresholdPx = 1f
    internal var phase by mutableStateOf(PullPhase.Idle)
    internal val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    /** 已拉下的距离 / 触发阈值；超过 1 表示松手即刷新。 */
    val distanceFraction: Float get() = offset.value / thresholdPx

    val isAnimating: Boolean get() = offset.isRunning

    /** 不经手势发起一次刷新（键盘快捷键）：和拉过阈值松手一样，内容让到转动的指示器下面直到刷新结束。 */
    fun refresh() {
        refreshRequests.trySend(Unit)
    }
}

internal enum class PullPhase {
    /** 没有用户发起的刷新：内容跟着手指走，松手弹回。 */
    Idle,

    /** 用户发起（或下拉时接手）的刷新正在进行：内容停在指示器下面。 */
    Refreshing,

    /** 这一轮刷新结束，内容正在弹回。 */
    Closing,
}

@Composable
fun rememberAppPullToRefreshState(): AppPullToRefreshState = remember { AppPullToRefreshState() }

private val RefreshThreshold = 72.dp
private val RefreshingOffset = 52.dp
private val SettleSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 300f)

/**
 * 下拉刷新（HIG Refresh content controls）：内容整体被拉下来，活动指示器出现在露出的空隙里，而不是悬在内容上方的浮标。
 * 拉的过程中环一点点绕出来，绕满（同时一下触感）就是拉过了阈值，松手触发 [onRefresh]；
 * 刷新期间内容停在转动的指示器下方，结束后弹回。
 *
 * 指示器是对用户这次操作的回应：[isRefreshing] 由别处置真（进页面、切标签、后台推送带来的自动刷新）时不动内容——
 * 那种刷新是安静的，列表为空时由页面自己在中间放指示器。
 * [enabled] 为假时不能发起新的刷新（批量选择这类列表不能在手底下变的场合）；已经在转的那一轮照常收尾。
 * [indicatorOffset] 是指示器的额外顶部偏移（内容从半透明顶栏下面开始时用）。
 */
@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: AppPullToRefreshState = rememberAppPullToRefreshState(),
    indicatorOffset: Dp = 0.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { RefreshThreshold.toPx() }
    val refreshingPx = with(density) { RefreshingOffset.toPx() }
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    state.thresholdPx = thresholdPx

    val connection = remember(state, scope, haptics, thresholdPx, refreshingPx) {
        PullToRefreshConnection(
            state = state,
            scope = scope,
            haptics = haptics,
            thresholdPx = thresholdPx,
            refreshingPx = refreshingPx,
            isRefreshing = { currentIsRefreshing },
            onRefresh = { currentOnRefresh() },
        )
    }
    connection.enabled = enabled

    LaunchedEffect(isRefreshing, connection) {
        if (!isRefreshing && state.phase == PullPhase.Refreshing) state.phase = PullPhase.Closing
        when (state.phase) {
            PullPhase.Refreshing -> state.offset.animateTo(refreshingPx, SettleSpring)
            PullPhase.Closing -> connection.close()
            // 自动刷新不动内容；拉到一半的距离归手势管，松手时再收
            PullPhase.Idle -> Unit
        }
    }
    LaunchedEffect(state, connection) {
        // 每次请求单开一个任务：收尾动画会被下一次拉动打断，不能连带结束这个监听
        for (request in state.refreshRequests) if (connection.enabled) launch { connection.holdForRefresh() }
    }

    Box(
        modifier
            .clipToBounds()
            .pointerInput(connection) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        connection.fingerDown = event.changes.any { it.pressed && it.type != PointerType.Mouse }
                    }
                }
            }
            .nestedScroll(connection),
        contentAlignment = contentAlignment,
    ) {
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
            if (state.phase == PullPhase.Idle) {
                AppActivityIndicator(progress = { state.distanceFraction }, modifier = Modifier.size(28.dp))
            } else {
                AppActivityIndicator(Modifier.size(28.dp))
            }
        }
        // 内容决定容器的尺寸（调用方可以不给尺寸），所以不能用 matchParentSize
        Box(
            Modifier.offset { IntOffset(0, state.offset.value.roundToInt()) },
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/** 把列表滚到顶之后剩下的滚动量变成下拉的距离，并在松手时决定是刷新还是弹回。 */
private class PullToRefreshConnection(
    private val state: AppPullToRefreshState,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val thresholdPx: Float,
    private val refreshingPx: Float,
    private val isRefreshing: () -> Boolean,
    private val onRefresh: () -> Unit,
) : NestedScrollConnection {
    var enabled = true

    /** 手指（触摸 / 触控笔）是不是正按在屏幕上。 */
    var fingerDown = false

    private var pastThreshold = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // 已经拉下来时往回推：先把拉下的距离还回去，再让列表滚动
        if (source != NestedScrollSource.UserInput || available.y >= 0f || state.offset.value <= 0f) return Offset.Zero
        val target = (state.offset.value + available.y).coerceAtLeast(0f)
        val consumed = target - state.offset.value
        pullTo(target)
        return Offset(0f, consumed)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        // 只有手指按着才算下拉。鼠标滚轮、触控板滚到顶同样会剩下滚动量，但它们没有"松手"，拉下去就回不来了
        if (!fingerDown || !enabled) return Offset.Zero
        // 橡皮筋阻尼：拉得越远越费力
        val resistance = 0.5f / (1f + state.offset.value / (thresholdPx * 2f))
        pullTo(state.offset.value + available.y * resistance)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val pulled = state.offset.value
        pastThreshold = false
        if (pulled <= 0f) {
            // 弹回途中又被手指接住、推回了顶：没有收尾动画可等，直接回到初始状态
            if (state.phase == PullPhase.Closing) state.phase = PullPhase.Idle
            return Velocity.Zero
        }
        // 收尾动画跑在组合的作用域里：它会被下一次拉动或刷新状态的变化打断，不能连带取消列表这一次滚动的收尾
        scope.launch {
            when {
                pulled >= thresholdPx -> holdForRefresh()
                state.phase == PullPhase.Refreshing -> state.offset.animateTo(refreshingPx, SettleSpring)
                else -> close()
            }
        }
        // 松手时的速度留给回弹，不再传给列表
        return available
    }

    /** 用户发起了一次刷新：内容让到指示器下面；已经有一轮刷新在跑就接手它，不重复发起。 */
    suspend fun holdForRefresh() {
        state.phase = PullPhase.Refreshing
        if (!isRefreshing()) onRefresh()
        state.offset.animateTo(refreshingPx, SettleSpring)
        // 停稳了刷新还没开始（请求被忽略，或一瞬间就结束了）：没有可等的，弹回去
        if (!isRefreshing()) {
            state.phase = PullPhase.Closing
            close()
        }
    }

    /** 内容弹回原位；被下一次拉动打断时状态留给那次拉动的松手来收。 */
    suspend fun close() {
        state.offset.animateTo(0f, SettleSpring)
        state.phase = PullPhase.Idle
    }

    private fun pullTo(target: Float) {
        // 刚拉过阈值的那一下给一次触感：松手就会刷新
        val reached = target >= thresholdPx
        if (reached && !pastThreshold && state.phase == PullPhase.Idle) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        pastThreshold = reached
        scope.launch { state.offset.snapTo(target) }
    }
}

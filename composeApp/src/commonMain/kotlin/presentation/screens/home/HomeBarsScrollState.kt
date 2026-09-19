package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 主页顶栏 / 底栏随列表滚动收起与显示的状态。
 *
 * - 向下滚（内容上移）：栏先收起，收完列表才开始滚；
 * - 向上滚：栏随上滚的距离逐步露出，露完列表才开始滚；
 * - 一次滚动结束（松手，或松手后的惯性停下）时没有完全露出就收回去；完全露出则一直留着，直到下一次向下滚。
 *
 * 顶部容器（身份栏 + 它下面那排标签）能收起的是状态栏以下的那一段：[collapseRange] 由布局量出来后写入。
 * 收到底时容器只剩状态栏那么高的一条留在屏幕上，给状态栏垫一层模糊底。
 */
@Stable
internal class HomeBarsScrollState {
    /** 顶部容器可收起的高度（px）。 */
    var collapseRange by mutableFloatStateOf(0f)
        private set

    /** 已经收起的高度（px）：0 = 完全显示，[collapseRange] = 完全收起。 */
    var collapsed by mutableFloatStateOf(0f)
        private set

    /** 收起进度 0..1，底栏按它整条滑出屏幕。 */
    val hiddenFraction: Float
        get() = if (collapseRange > 0f) collapsed / collapseRange else 0f

    /** 顶部容器高度变了（字号、窗口变化、换到没有标签的页面）：保持收起进度不变。 */
    fun updateCollapseRange(range: Float) {
        val fraction = hiddenFraction
        collapseRange = range.coerceAtLeast(0f)
        collapsed = collapseRange * fraction
    }

    /**
     * 列表的滚动量先交给栏消耗：[delta] 小于 0 是向下滚（收起），大于 0 是向上滚（露出）。
     * 返回栏消耗掉的那部分，余下的才归列表。
     */
    fun consumeScroll(delta: Float): Float {
        val target = (collapsed - delta).coerceIn(0f, collapseRange)
        val consumed = collapsed - target
        collapsed = target
        return consumed
    }

    /** 一次滚动结束：没有完全露出就收回去。 */
    suspend fun settle(animationSpec: AnimationSpec<Float>) {
        if (collapsed == 0f || collapsed == collapseRange) return
        animateCollapsedTo(collapseRange, animationSpec)
    }

    /** 切换页面等场合把栏放出来。 */
    suspend fun show(animationSpec: AnimationSpec<Float>) {
        if (collapsed == 0f) return
        animateCollapsedTo(0f, animationSpec)
    }

    private suspend fun animateCollapsedTo(target: Float, animationSpec: AnimationSpec<Float>) {
        animate(initialValue = collapsed, targetValue = target, animationSpec = animationSpec) { value, _ ->
            collapsed = value.coerceIn(0f, collapseRange)
        }
    }
}

/**
 * 把子树里列表的滚动接到 [state] 上。收尾动画跑在 [scope] 里：手指重新按上来时要能立刻打断它，
 * 否则动画和手势会抢着改同一个值。
 */
internal class HomeBarsNestedScrollConnection(
    private val state: HomeBarsScrollState,
    private val scope: CoroutineScope,
    private val animationSpec: () -> AnimationSpec<Float>,
) : NestedScrollConnection {
    private var settleJob: Job? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y == 0f) return Offset.Zero
        if (source == NestedScrollSource.UserInput) settleJob?.cancel()
        return Offset(0f, state.consumeScroll(available.y))
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle()
        return Velocity.Zero
    }

    fun show() {
        settleJob?.cancel()
        settleJob = scope.launch { state.show(animationSpec()) }
    }

    private fun settle() {
        settleJob?.cancel()
        settleJob = scope.launch { state.settle(animationSpec()) }
    }
}

@Composable
internal fun rememberHomeBarsNestedScrollConnection(state: HomeBarsScrollState): HomeBarsNestedScrollConnection {
    val scope = rememberCoroutineScope()
    val durationMs = AppTheme.motion.normalMs
    return remember(state, scope, durationMs) {
        HomeBarsNestedScrollConnection(state, scope) { tween(durationMs) }
    }
}

/**
 * 顶部容器：按收起的高度整条上移；占位高度不变，骨架量到的顶栏高度始终是完整高度。
 * 容器本身（模糊底）不淡出——收到底时留在屏幕上的那一条正好垫在状态栏下面；淡出的是里面的内容，见 [fadeOutWith]。
 */
internal fun Modifier.collapseUpwardWith(state: HomeBarsScrollState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(0, -state.collapsed.roundToInt())
    }
}

/** 顶部容器里的内容（身份栏、标签）随收起进度淡出。 */
internal fun Modifier.fadeOutWith(state: HomeBarsScrollState): Modifier =
    graphicsLayer { alpha = 1f - state.hiddenFraction }

/** 底栏：按收起进度整条滑出屏幕底部。 */
internal fun Modifier.slideOutDownwardWith(state: HomeBarsScrollState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.place(0, (placeable.height * state.hiddenFraction).roundToInt())
    }
}

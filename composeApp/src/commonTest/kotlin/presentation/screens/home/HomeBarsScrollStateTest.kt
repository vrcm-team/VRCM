package io.github.vrcmteam.vrcm.presentation.screens.home

import androidx.compose.animation.core.snap
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 主页顶栏 / 底栏的滚动规则：栏先于列表消耗滚动量；一次滚动结束时只有完全露出才留下，否则收回。
 */
class HomeBarsScrollStateTest {
    @Test
    fun scrollingDownCollapsesTheBarsBeforeTheListScrolls() {
        val state = barsState(range = 100f)

        assertEquals(-60f, state.consumeScroll(-60f))
        assertEquals(0.6f, state.hiddenFraction)
        // 只剩 40 可收：多出来的 30 留给列表
        assertEquals(-40f, state.consumeScroll(-70f))
        assertEquals(1f, state.hiddenFraction)
        assertEquals(0f, state.consumeScroll(-25f))
    }

    @Test
    fun scrollingUpRevealsTheBarsByTheDistanceScrolled() {
        val state = barsState(range = 100f).apply { consumeScroll(-100f) }

        assertEquals(30f, state.consumeScroll(30f))
        assertEquals(0.7f, state.hiddenFraction)
        assertEquals(70f, state.consumeScroll(90f))
        assertEquals(0f, state.hiddenFraction)
    }

    @Test
    fun partiallyRevealedBarsHideAgainWhenTheScrollEnds() = runTest {
        val state = barsState(range = 100f).apply {
            consumeScroll(-100f)
            consumeScroll(80f)
        }

        withContext(ImmediateFrameClock()) { state.settle(snap()) }

        assertEquals(1f, state.hiddenFraction)
    }

    @Test
    fun fullyRevealedBarsStayUntilTheNextScrollDown() = runTest {
        val state = barsState(range = 100f).apply {
            consumeScroll(-100f)
            consumeScroll(100f)
        }

        withContext(ImmediateFrameClock()) { state.settle(snap()) }
        assertEquals(0f, state.hiddenFraction)

        state.consumeScroll(-10f)
        withContext(ImmediateFrameClock()) { state.settle(snap()) }
        assertEquals(1f, state.hiddenFraction)
    }

    @Test
    fun collapseProgressSurvivesATopBarHeightChange() {
        val state = barsState(range = 100f).apply { consumeScroll(-50f) }

        state.updateCollapseRange(160f)

        assertEquals(0.5f, state.hiddenFraction)
        assertEquals(80f, state.collapsed)
    }

    private fun barsState(range: Float) = HomeBarsScrollState().apply { updateCollapseRange(range) }

    /** 动画需要帧时钟：每次请求立刻给下一帧。 */
    private class ImmediateFrameClock : MonotonicFrameClock {
        private var frameTimeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            frameTimeNanos += 16_000_000L
            return onFrame(frameTimeNanos)
        }
    }
}

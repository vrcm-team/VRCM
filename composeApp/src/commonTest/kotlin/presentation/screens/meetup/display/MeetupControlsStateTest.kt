package io.github.vrcmteam.vrcm.presentation.screens.meetup.display

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MeetupControlsStateTest {

    @Test
    fun interactionShowsControlsAndTimeoutHidesThem() = runTest {
        val state = MeetupControlsState(backgroundScope, timeout = 3.seconds)
        state.onInteraction()
        assertTrue(state.visible.value)
        advanceTimeBy(2_999)
        assertTrue(state.visible.value)
        // advanceTimeBy 不执行恰好落在目标时刻的任务，runCurrent 补跑 t=3000 的隐藏任务
        advanceTimeBy(1)
        runCurrent()
        assertFalse(state.visible.value)
    }

    @Test
    fun anotherInteractionRestartsTimeout() = runTest {
        val state = MeetupControlsState(backgroundScope, timeout = 3.seconds)
        state.onInteraction()
        advanceTimeBy(2_000)
        state.onInteraction()
        advanceTimeBy(2_000)
        // 旧计时（t=3000）已被取消，距新交互仅过去 2 秒，仍然可见
        assertTrue(state.visible.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(state.visible.value)
    }

    @Test
    fun closeCancelsHideJobAndBlocksFurtherInteractions() = runTest {
        val state = MeetupControlsState(backgroundScope, timeout = 3.seconds)
        state.onInteraction()
        state.close()
        assertFalse(state.visible.value)
        // close 后交互不再显示，也不再产生新的隐藏 Job
        state.onInteraction()
        assertFalse(state.visible.value)
        advanceUntilIdle()
        assertFalse(state.visible.value)
    }
}

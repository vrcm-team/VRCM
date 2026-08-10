package io.github.vrcmteam.vrcm.service

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FriendActivityTrackingStateTest {
    @Test
    fun backgroundMonitorRestartResumesTrackingWhileAppRemainsStopped() = runTest {
        var nowMillis = 1_000L
        val state = FriendActivityTrackingState { nowMillis }

        state.setAppForeground(true)
        state.setBackgroundMonitoring(true)
        state.setAppForeground(false)
        assertTrue(state.isEnabled())
        assertEquals(
            FriendActivityTrackingTransition(1L, FriendActivityTrackingControl.Resume, 1_000L),
            state.controls.first(),
        )

        nowMillis = 2_000L
        state.setBackgroundMonitoring(false)
        assertFalse(state.isEnabled())
        assertEquals(
            FriendActivityTrackingTransition(2L, FriendActivityTrackingControl.Stop, 2_000L),
            state.controls.first(),
        )

        nowMillis = 3_000L
        state.setBackgroundMonitoring(true)
        assertTrue(state.isEnabled())
        assertEquals(
            FriendActivityTrackingTransition(3L, FriendActivityTrackingControl.Resume, 3_000L),
            state.controls.first(),
        )
    }
}

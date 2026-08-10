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
        val state = FriendActivityTrackingState()

        state.setAppForeground(true)
        state.setBackgroundMonitoring(true)
        state.setAppForeground(false)
        assertTrue(state.isEnabled())
        assertEquals(FriendActivityTrackingControl.Resume, state.controls.first())

        state.setBackgroundMonitoring(false)
        assertFalse(state.isEnabled())
        assertEquals(FriendActivityTrackingControl.Stop, state.controls.first())

        state.setBackgroundMonitoring(true)
        assertTrue(state.isEnabled())
        assertEquals(FriendActivityTrackingControl.Resume, state.controls.first())
    }
}

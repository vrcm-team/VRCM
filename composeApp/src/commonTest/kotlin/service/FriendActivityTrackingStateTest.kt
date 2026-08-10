package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendActivityTrackingStateTest {
    @Test
    fun backgroundMonitorRestartResumesTrackingWhileAppRemainsStopped() {
        val state = FriendActivityTrackingState()

        assertEquals(FriendActivityTrackingControl.Resume, state.setAppForeground(true))
        assertNull(state.setBackgroundMonitoring(true))
        assertNull(state.setAppForeground(false))
        assertTrue(state.isEnabled())

        assertEquals(FriendActivityTrackingControl.Stop, state.setBackgroundMonitoring(false))
        assertFalse(state.isEnabled())
        assertEquals(FriendActivityTrackingControl.Resume, state.setBackgroundMonitoring(true))
        assertTrue(state.isEnabled())
    }
}

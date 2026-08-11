package io.github.vrcmteam.vrcm.network.websocket

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebSocketConnectionPolicyTest {
    @Test
    fun configuredMonitoringKeepsConnectionBeforeServiceStartCompletes() {
        val policy = WebSocketConnectionPolicy()

        policy.onBackground(isMonitoringConfigured = true)

        assertTrue(policy.shouldKeepConnection)
    }

    @Test
    fun backgroundConnectionStopsOnlyWhenMonitoringIsNeitherConfiguredNorActive() {
        val policy = WebSocketConnectionPolicy()

        policy.onBackground(isMonitoringConfigured = false)
        assertFalse(policy.shouldKeepConnection)

        policy.setBackgroundServiceActive(true)
        assertTrue(policy.shouldKeepConnection)

        policy.setBackgroundServiceActive(false)
        assertFalse(policy.shouldKeepConnection)

        assertTrue(policy.onForeground())
        assertTrue(policy.shouldKeepConnection)
        assertFalse(policy.onForeground())
    }
}

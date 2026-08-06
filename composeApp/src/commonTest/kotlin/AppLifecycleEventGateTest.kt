package io.github.vrcmteam.vrcm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLifecycleEventGateTest {

    @Test
    fun configurationChangeDoesNotDispatchBackgroundOrForeground() {
        val gate = AppLifecycleEventGate()

        assertFalse(gate.onStop(isConfigurationChange = true))
        assertFalse(gate.onResume())
    }

    @Test
    fun actualBackgroundTransitionDispatchesEachEventOnce() {
        val gate = AppLifecycleEventGate()

        assertTrue(gate.onStop(isConfigurationChange = false))
        assertFalse(gate.onStop(isConfigurationChange = false))
        assertTrue(gate.onResume())
        assertFalse(gate.onResume())
    }
}

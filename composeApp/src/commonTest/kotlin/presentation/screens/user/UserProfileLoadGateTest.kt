package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfileLoadGateTest {
    @Test
    fun failedUserLoadCanBeRetried() {
        val gate = UserProfileLoadGate()

        assertTrue(gate.tryStart())
        gate.markRetryable()

        assertTrue(gate.tryStart())
    }

    @Test
    fun loadedUserIsCachedUntilAForcedRefresh() {
        val gate = UserProfileLoadGate()

        assertTrue(gate.tryStart())
        gate.markLoaded()

        assertFalse(gate.tryStart())
        assertTrue(gate.tryStart(forceRefresh = true))
    }

    @Test
    fun inFlightUserLoadIsNotDuplicated() {
        val gate = UserProfileLoadGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        assertFalse(gate.tryStart(forceRefresh = true))
    }
}

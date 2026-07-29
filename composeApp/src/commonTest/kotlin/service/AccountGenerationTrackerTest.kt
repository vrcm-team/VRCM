package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountGenerationTrackerTest {
    @Test
    fun switchingAccountInvalidatesCapturedWorkAndActivatesLatestAccount() {
        val tracker = AccountGenerationTracker("usr_a")
        val accountA = tracker.currentToken()!!

        val accountB = tracker.activate("usr_b")

        assertTrue(accountB.changed)
        assertFalse(tracker.isCurrent(accountA))
        assertTrue(tracker.isCurrent(accountB.token))
    }

    @Test
    fun logoutInvalidatesInFlightWork() {
        val tracker = AccountGenerationTracker("usr_a")
        val accountA = tracker.currentToken()!!

        tracker.clear()

        assertFalse(tracker.isCurrent(accountA))
        assertTrue(tracker.currentToken() == null)
    }
}

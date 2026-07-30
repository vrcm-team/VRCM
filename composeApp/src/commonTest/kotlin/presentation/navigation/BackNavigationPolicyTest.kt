package io.github.vrcmteam.vrcm.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackNavigationPolicyTest {
    @Test
    fun backNavigationRemainsDisabledUntilEveryBlockerIsReleased() {
        val policy = BackNavigationPolicy()
        val firstBlocker = Any()
        val secondBlocker = Any()

        assertTrue(policy.isBackNavigationEnabled)

        policy.setBlocked(firstBlocker, blocked = true)
        policy.setBlocked(secondBlocker, blocked = true)
        assertFalse(policy.isBackNavigationEnabled)

        policy.setBlocked(firstBlocker, blocked = false)
        assertFalse(policy.isBackNavigationEnabled)

        policy.setBlocked(secondBlocker, blocked = false)
        assertTrue(policy.isBackNavigationEnabled)
    }

    @Test
    fun updatingTheSameBlockerIsIdempotent() {
        val policy = BackNavigationPolicy()
        val blocker = Any()

        policy.setBlocked(blocker, blocked = true)
        policy.setBlocked(blocker, blocked = true)
        policy.setBlocked(blocker, blocked = false)

        assertTrue(policy.isBackNavigationEnabled)
    }
}

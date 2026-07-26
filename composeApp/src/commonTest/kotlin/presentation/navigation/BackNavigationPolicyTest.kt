package io.github.vrcmteam.vrcm.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackNavigationPolicyTest {
    @Test
    fun slideBackRemainsDisabledUntilEveryBlockerIsReleased() {
        val policy = BackNavigationPolicy()
        val firstBlocker = Any()
        val secondBlocker = Any()

        assertTrue(policy.isSlideBackEnabled)

        policy.setBlocked(firstBlocker, blocked = true)
        policy.setBlocked(secondBlocker, blocked = true)
        assertFalse(policy.isSlideBackEnabled)

        policy.setBlocked(firstBlocker, blocked = false)
        assertFalse(policy.isSlideBackEnabled)

        policy.setBlocked(secondBlocker, blocked = false)
        assertTrue(policy.isSlideBackEnabled)
    }

    @Test
    fun updatingTheSameBlockerIsIdempotent() {
        val policy = BackNavigationPolicy()
        val blocker = Any()

        policy.setBlocked(blocker, blocked = true)
        policy.setBlocked(blocker, blocked = true)
        policy.setBlocked(blocker, blocked = false)

        assertTrue(policy.isSlideBackEnabled)
    }
}

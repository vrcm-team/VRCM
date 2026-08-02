package io.github.vrcmteam.vrcm.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun firstBackClosesDialogAndSecondBackNavigates() {
        val policy = BackNavigationPolicy()
        val dialog = Any()
        var dialogCloseCount = 0
        var navigationCount = 0
        policy.setBackHandler(dialog) {
            dialogCloseCount += 1
            policy.setBackHandler(dialog, handler = null)
        }

        assertTrue(policy.handleBack(canNavigateBack = true) { navigationCount += 1 })
        assertEquals(1, dialogCloseCount)
        assertEquals(0, navigationCount)

        assertTrue(policy.handleBack(canNavigateBack = true) { navigationCount += 1 })
        assertEquals(1, dialogCloseCount)
        assertEquals(1, navigationCount)
    }

    @Test
    fun blockedBackIsConsumedWithoutNavigating() {
        val policy = BackNavigationPolicy()
        val blocker = Any()
        var navigationCount = 0
        policy.setBlocked(blocker, blocked = true)

        assertTrue(policy.shouldHandleBack(canNavigateBack = true))
        assertTrue(policy.handleBack(canNavigateBack = true) { navigationCount += 1 })
        assertEquals(0, navigationCount)
    }
}

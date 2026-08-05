package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfileEntranceAnimationTest {
    @Test
    fun sameNavigationEntryOnlyAnimatesItsFirstComposition() {
        val gate = OneShotEntranceAnimationGate()

        assertTrue(gate.consume())
        assertFalse(gate.consume())
    }
}

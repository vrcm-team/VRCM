package io.github.vrcmteam.vrcm.presentation.screens.group

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupProfileInitialLoadGateTest {
    @Test
    fun sameGroupRunsInitialLoadOnlyOnce() = runTest {
        val gate = GroupProfileInitialLoadGate()
        var loadCount = 0

        assertTrue(gate.runIfNeeded("grp_test") { loadCount++ })
        assertFalse(gate.runIfNeeded("grp_test") { loadCount++ })

        assertEquals(1, loadCount)
    }
}

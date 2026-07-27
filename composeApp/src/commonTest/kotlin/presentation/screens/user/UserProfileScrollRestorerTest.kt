package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfileScrollRestorerTest {
    @Test
    fun restoredPositionIsNotAppliedAgainWhenContentHeightChanges() {
        val restorer = OneShotScrollRestorer(savedPosition = 240)

        assertNull(restorer.consume(maxValue = Int.MAX_VALUE))
        assertNull(restorer.consume(maxValue = 0))
        assertNull(restorer.consume(maxValue = 120))
        assertEquals(240, restorer.consume(maxValue = 480))
        assertNull(restorer.consume(maxValue = 720))
    }

    @Test
    fun zeroPositionDoesNotScheduleRestoration() {
        val restorer = OneShotScrollRestorer(savedPosition = 0)

        assertNull(restorer.consume(maxValue = 480))
    }
}

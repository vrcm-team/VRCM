package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProfileSharedKeyTest {
    @Test
    fun hiddenWorldDoesNotRegisterAWorldImageSharedKey() {
        assertNull(worldImageSharedKey("Fav_", "???"))
    }

    @Test
    fun visibleWorldUsesThePrefixedWorldImageSharedKey() {
        assertEquals(
            "Created_wrld_exampleWorldImage",
            worldImageSharedKey("Created_", "wrld_example"),
        )
    }

    @Test
    fun singleStackedCardSharesDirectlyWithItsItemDetail() {
        assertTrue(shouldShareStackedCardWithItemDetail(itemCount = 1))
    }

    @Test
    fun multiItemStackOnlySharesWithTheExpandedList() {
        assertFalse(shouldShareStackedCardWithItemDetail(itemCount = 2))
    }
}

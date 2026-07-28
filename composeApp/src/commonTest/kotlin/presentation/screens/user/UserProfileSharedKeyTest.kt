package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}

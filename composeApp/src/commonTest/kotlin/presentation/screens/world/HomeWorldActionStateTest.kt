package io.github.vrcmteam.vrcm.presentation.screens.world

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.service.HomeWorldUserContext
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeWorldActionStateTest {
    private val user = HomeWorldUserContext(
        sessionToken = AccountSessionToken(userId = "usr_owner", generation = 1),
        homeLocation = "wrld_current",
    )

    @Test
    fun currentHomeWorldOffersReset() {
        assertEquals(
            HomeWorldActionAvailability.Current,
            homeWorldActionAvailability("wrld_current", user),
        )
    }

    @Test
    fun anotherValidWorldOffersSet() {
        assertEquals(
            HomeWorldActionAvailability.CanSet,
            homeWorldActionAvailability("wrld_other", user),
        )
    }

    @Test
    fun missingSessionOrInvalidWorldDisablesTheAction() {
        assertEquals(
            HomeWorldActionAvailability.Unavailable,
            homeWorldActionAvailability("wrld_other", null),
        )
        assertEquals(
            HomeWorldActionAvailability.Unavailable,
            homeWorldActionAvailability("avtr_not-a-world", user),
        )
    }
}

package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.auth.data.Presence
import io.github.vrcmteam.vrcm.network.api.attributes.UserState
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentUserWebPresenceTest {
    @Test
    fun activeSocketStateClearsStaleGameLocation() {
        val presence = gamePresence().withSocketUserState(UserState.Active.value)

        assertEquals("", presence.world)
        assertEquals("offline", presence.instance)
        assertEquals("", presence.travelingToWorld)
        assertEquals("", presence.travelingToInstance)
    }

    @Test
    fun onlineSocketStateKeepsGameLocation() {
        assertEquals(gamePresence(), gamePresence().withSocketUserState(UserState.Online.value))
    }

    @Test
    fun emptyLocationWithoutTravelingBecomesWebsitePresence() {
        assertEquals("offline", normalizeOwnLocation("", ""))
        assertEquals("traveling", normalizeOwnLocation("traveling", "wrld_next:instance"))
        assertEquals("offline", normalizeOwnLocation("traveling", ""))
        assertEquals("private", normalizeOwnLocation("private", ""))
    }

    @Test
    fun laterRestRefreshCannotRestoreLocationAfterSocketWentToWeb() {
        val staleRestPresence = gamePresence()
        val webSocketPresence = gamePresence().withSocketUserState(UserState.Active.value)

        assertEquals(webSocketPresence, selectCurrentPresence(staleRestPresence, webSocketPresence))
        assertEquals(staleRestPresence, selectCurrentPresence(staleRestPresence, null))
    }

    private fun gamePresence() = Presence(
        avatarThumbnail = null,
        displayName = "Current user",
        groups = emptyList(),
        id = "usr_self",
        instance = "instance",
        instanceType = "public",
        isRejoining = null,
        platform = "standalonewindows",
        profilePicOverride = null,
        status = "active",
        travelingToInstance = "destination-instance",
        travelingToWorld = "wrld_destination",
        world = "wrld_current",
    )
}

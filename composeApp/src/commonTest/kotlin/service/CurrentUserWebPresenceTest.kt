package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.auth.data.Presence
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentUserWebPresenceTest {
    @Test
    fun laterRestRefreshCannotOverrideSocketLocation() {
        val staleRestPresence = gamePresence()
        val socketPresence = gamePresence().copy(
            world = "",
            instance = "traveling",
            travelingToWorld = "",
            travelingToInstance = "",
        )

        assertEquals(socketPresence, selectCurrentPresence(staleRestPresence, socketPresence))
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

package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.api.auth.data.Presence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLocationTest {
    @Test
    fun activePresenceBuildsAFullLocationWithoutDroppingInstanceTags() {
        val presence = presence(
            world = "wrld_origin",
            instance = "12345~hidden(usr_owner)~region(use)~nonce(value)",
        )

        assertEquals(
            "wrld_origin:12345~hidden(usr_owner)~region(use)~nonce(value)",
            presence.inviteLocationOrNull(),
        )
    }

    @Test
    fun travelingPresenceUsesItsDestinationLocation() {
        val presence = presence(
            instance = "traveling",
            travelingToWorld = "wrld_destination",
            travelingToInstance = "67890~region(jp)",
        )

        assertEquals(
            "wrld_destination:67890~region(jp)",
            presence.inviteLocationOrNull(),
        )
    }

    @Test
    fun presenceWithoutACompleteActiveLocationCannotBeInvitedTo() {
        assertNull(presence(instance = "offline").inviteLocationOrNull())
        assertNull(presence(instance = "private").inviteLocationOrNull())
        assertNull(presence(instance = "12345~region(use)").inviteLocationOrNull())
        assertNull(presence(instance = "traveling").inviteLocationOrNull())
    }

    private fun presence(
        world: String = "",
        instance: String,
        travelingToWorld: String = "",
        travelingToInstance: String = "",
    ) = Presence(
        avatarThumbnail = null,
        displayName = null,
        groups = emptyList(),
        id = "usr_current",
        instance = instance,
        instanceType = "",
        isRejoining = null,
        platform = "standalonewindows",
        profilePicOverride = null,
        status = "active",
        travelingToInstance = travelingToInstance,
        travelingToWorld = travelingToWorld,
        world = world,
    )
}

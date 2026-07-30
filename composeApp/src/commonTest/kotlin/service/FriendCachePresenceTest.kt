package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendCachePresenceTest {
    @Test
    fun cachedOnlinePresenceIsRestoredAsOffline() {
        val restored = friend(
            location = "wrld_stale:123",
            travelingToLocation = "wrld_destination:456",
        ).asCachedOffline()

        assertEquals(LocationType.Offline.value, restored.location)
        assertEquals("", restored.travelingToLocation)
        assertEquals(UserStatus.Offline, restored.status)
    }

    private fun friend(location: String, travelingToLocation: String) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = "Cached friend",
        friendKey = "",
        id = "usr_friend",
        imageUrl = "",
        isFriend = true,
        lastLogin = "",
        lastPlatform = "standalonewindows",
        location = location,
        travelingToLocation = travelingToLocation,
        profilePicOverride = "",
        status = UserStatus.Active,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}

package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.network.api.attributes.AgeVerificationStatus
import io.github.vrcmteam.vrcm.network.api.attributes.FriendRequestStatus
import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserState
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfileCachedPresenceTest {
    @Test
    fun cachedProfileClearsHistoricalPresence() {
        val cachedUser = onlineUser().asCachedOffline()

        assertEquals(UserState.Offline, cachedUser.state)
        assertEquals(UserStatus.Offline, cachedUser.status)
        assertEquals(LocationType.Offline.value, cachedUser.location)
        assertEquals("", cachedUser.instanceId)
        assertEquals("", cachedUser.worldId)
        assertNull(cachedUser.travelingToInstance)
        assertNull(cachedUser.travelingToLocation)
        assertNull(cachedUser.travelingToWorld)
    }

    @Test
    fun currentFriendSnapshotOverridesCachedOfflinePresence() {
        val cachedProfile = UserProfileVo(onlineUser().asCachedOffline())
        val currentFriend = friend(location = "wrld_current:2")

        val profile = cachedProfile.withCurrentFriendPresence(currentFriend)

        assertEquals(UserStatus.JoinMe, profile.status)
        assertEquals("Current status", profile.statusDescription)
        assertEquals("wrld_current:2", profile.location)
        assertEquals("2026-07-30T01:02:03.000Z", profile.lastLogin)
        assertEquals("android", profile.lastPlatform)
    }

    private fun onlineUser() = UserData(
        ageVerificationStatus = AgeVerificationStatus.Verified,
        allowAvatarCopying = true,
        bio = "Bio",
        bioLinks = emptyList(),
        currentAvatarImageUrl = "",
        currentAvatarTags = emptyList(),
        currentAvatarThumbnailImageUrl = "",
        dateJoined = "2020-01-01",
        developerType = "none",
        displayName = "Friend",
        friendKey = "",
        friendRequestStatus = FriendRequestStatus.Null,
        id = "usr_friend",
        instanceId = "instance-old",
        isFriend = true,
        lastActivity = "2026-07-29T01:02:03.000Z",
        lastLogin = "2026-07-29T01:02:03.000Z",
        lastPlatform = "standalonewindows",
        location = "wrld_old:1",
        note = "",
        profilePicOverride = "",
        state = UserState.Online,
        status = UserStatus.Active,
        statusDescription = "Cached status",
        tags = emptyList(),
        travelingToInstance = "instance-traveling",
        travelingToLocation = "wrld_destination:3",
        travelingToWorld = "wrld_destination",
        userIcon = "",
        worldId = "wrld_old",
        pronouns = null,
    )

    private fun friend(location: String) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = "Friend",
        friendKey = "",
        id = "usr_friend",
        imageUrl = "",
        isFriend = true,
        lastLogin = "2026-07-30T01:02:03.000Z",
        lastPlatform = "android",
        location = location,
        profilePicOverride = "",
        status = UserStatus.JoinMe,
        statusDescription = "Current status",
        userIcon = "",
        pronouns = null,
    )
}

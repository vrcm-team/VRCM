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

    @Test
    fun cachedRestoreKeepsLivePresenceWhenUserIsNotInFriendMap() {
        // 自己的资料页在好友表里没有条目，缓存回填不能把页面上的实时状态压回离线。
        val livePresence = UserProfileVo(
            id = "usr_friend",
            status = UserStatus.Active,
            statusDescription = "Live status",
            location = LocationType.Offline.value,
        )

        val profile = mergeCachedProfile(
            cachedUser = onlineUser(),
            livePresence = livePresence,
            currentFriend = null,
        )

        assertEquals(UserStatus.Active, profile.status)
        assertEquals("Live status", profile.statusDescription)
        assertEquals(LocationType.Offline.value, profile.location)
        // 静态资料仍然由缓存回填。
        assertEquals("Bio", profile.bio)
    }

    @Test
    fun cachedRestorePrefersFriendSnapshotOverNavigationPresence() {
        val livePresence = UserProfileVo(
            id = "usr_friend",
            status = UserStatus.Active,
            statusDescription = "Stale status",
            location = LocationType.Offline.value,
        )

        val profile = mergeCachedProfile(
            cachedUser = onlineUser(),
            livePresence = livePresence,
            currentFriend = friend(location = "wrld_current:2"),
        )

        assertEquals(UserStatus.JoinMe, profile.status)
        assertEquals("Current status", profile.statusDescription)
        assertEquals("wrld_current:2", profile.location)
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

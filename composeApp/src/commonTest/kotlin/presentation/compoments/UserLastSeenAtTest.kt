package io.github.vrcmteam.vrcm.presentation.compoments

import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.attributes.lastSeenAt
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.network.api.users.data.MutualFriendData
import io.github.vrcmteam.vrcm.network.api.users.data.SearchUserData
import kotlin.test.Test
import kotlin.test.assertEquals

class UserLastSeenAtTest {
    @Test
    fun friendUsesLastActivityWhenAvailable() {
        val friend = friend(lastActivity = "2026-07-30T08:00:00Z", lastLogin = "2026-07-29T08:00:00Z")

        assertEquals("2026-07-30T08:00:00Z", friend.lastSeenAt())
    }

    @Test
    fun searchUserFallsBackToLastLogin() {
        val user = SearchUserData(
            currentAvatarImageUrl = "",
            currentAvatarThumbnailImageUrl = "",
            developerType = "none",
            displayName = "Search user",
            id = "usr_search",
            isFriend = false,
            lastPlatform = "standalonewindows",
            profilePicOverride = "",
            pronouns = null,
            status = UserStatus.Offline,
            statusDescription = "",
            tags = emptyList(),
            userIcon = "",
            lastLogin = "2026-07-28T08:00:00Z",
        )

        assertEquals("2026-07-28T08:00:00Z", user.lastSeenAt())
    }

    @Test
    fun mutualFriendFallsBackToLastLogin() {
        val user = MutualFriendData(
            id = "usr_mutual",
            lastLogin = "2026-07-27T08:00:00Z",
        )

        assertEquals("2026-07-27T08:00:00Z", user.lastSeenAt())
    }

    private fun friend(lastActivity: String, lastLogin: String) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = "Friend",
        friendKey = "",
        id = "usr_friend",
        imageUrl = "",
        isFriend = true,
        lastActivity = lastActivity,
        lastLogin = lastLogin,
        lastPlatform = "standalonewindows",
        location = "offline",
        profilePicOverride = "",
        status = UserStatus.Offline,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}

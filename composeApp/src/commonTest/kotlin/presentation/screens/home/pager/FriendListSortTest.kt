package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendListSortTest {
    @Test
    fun offlineFriendsFallBackToLastLoginWhenLastActivityIsMissing() {
        val older = friend(
            id = "usr_older",
            lastActivity = "",
            lastLogin = "2026-07-20T08:00:00Z",
        )
        val newer = friend(
            id = "usr_newer",
            lastActivity = "",
            lastLogin = "2026-07-29T08:00:00Z",
        )

        val sorted = listOf(older, newer).sortedUserByStatus()

        assertEquals(listOf("usr_newer", "usr_older"), sorted.map(FriendData::id))
    }

    private fun friend(id: String, lastActivity: String, lastLogin: String) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = id,
        friendKey = "",
        id = id,
        imageUrl = "",
        isFriend = true,
        lastActivity = lastActivity,
        lastLogin = lastLogin,
        lastPlatform = "standalonewindows",
        location = LocationType.Offline.value,
        profilePicOverride = "",
        status = UserStatus.Offline,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}

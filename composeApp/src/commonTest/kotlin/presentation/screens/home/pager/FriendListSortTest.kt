package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendListSortTest {
    @Test
    fun inGameFriendsComeBeforeWebAndOfflineFriends() {
        val offline = friend(
            id = "usr_offline",
            status = UserStatus.Offline,
            location = LocationType.Offline.value,
        )
        val legacyWeb = friend(
            id = "usr_legacy_web",
            status = UserStatus.Active,
            location = LocationType.Offline.value,
        )
        val web = friend(
            id = "usr_web",
            status = UserStatus.Active,
            location = LocationType.Web.value,
        )
        val inGame = friend(
            id = "usr_in_game",
            status = UserStatus.Active,
            location = "wrld_world:instance",
        )

        val sorted = listOf(offline, legacyWeb, web, inGame).sortedUserByStatus()

        assertEquals(
            listOf("usr_in_game", "usr_web", "usr_legacy_web", "usr_offline"),
            sorted.map(FriendData::id),
        )
    }

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

    private fun friend(
        id: String,
        lastActivity: String = "",
        lastLogin: String = "",
        status: UserStatus = UserStatus.Offline,
        location: String = LocationType.Offline.value,
    ) = FriendData(
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
        location = location,
        profilePicOverride = "",
        status = status,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}

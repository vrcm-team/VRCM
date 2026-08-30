package io.github.vrcmteam.vrcm.presentation.screens.home.pager

import io.github.vrcmteam.vrcm.network.api.attributes.LocationType
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendDirectoryGroupsTest {
    @Test
    fun onlinePresenceBoundariesProduceDirectorySections() {
        val groups = buildFriendDirectoryGroups(
            friends = listOf(
                friend("game", "wrld_test:1", UserStatus.Active),
                friend("traveling", LocationType.Traveling.value, UserStatus.Active),
                friend("web", LocationType.Web.value, UserStatus.Active),
                friend("legacy-web", LocationType.Offline.value, UserStatus.Active),
                friend("private", LocationType.Private.value, UserStatus.Active),
                friend("offline", "wrld_stale:1", UserStatus.Offline),
            ),
            query = "",
            favoriteIds = null,
        )

        assertEquals(
            mapOf(
                FriendDirectorySection.InGame to listOf("game", "traveling"),
                FriendDirectorySection.Web to listOf("legacy-web", "web"),
                FriendDirectorySection.Private to listOf("private"),
                FriendDirectorySection.Offline to listOf("offline"),
            ),
            groups.associate { group -> group.section to group.friends.map(FriendData::id) },
        )
    }

    @Test
    fun searchAndFavoriteGroupAreAppliedToTheSameFriendSnapshot() {
        val groups = buildFriendDirectoryGroups(
            friends = listOf(
                friend("alice", LocationType.Web.value, displayName = "Alice"),
                friend("alina", LocationType.Private.value, displayName = "Alina"),
                friend("bob", LocationType.Web.value, displayName = "Bob"),
            ),
            query = "ali",
            favoriteIds = setOf("alina", "bob"),
        )

        assertEquals(listOf("alina"), groups.flatMap(FriendDirectoryGroup::friends).map(FriendData::id))
    }

    @Test
    fun offlineFriendsAreOrderedByLatestActivityThenName() {
        val groups = buildFriendDirectoryGroups(
            friends = listOf(
                friend("older", LocationType.Offline.value, lastActivity = "2026-01-01T00:00:00Z"),
                friend("z-new", LocationType.Offline.value, lastActivity = "2026-08-01T00:00:00Z"),
                friend("a-new", LocationType.Offline.value, lastActivity = "2026-08-01T00:00:00Z"),
            ),
            query = "",
            favoriteIds = null,
        )

        assertEquals(
            listOf("a-new", "z-new", "older"),
            groups.single().friends.map(FriendData::id),
        )
    }

    private fun friend(
        id: String,
        location: String,
        status: UserStatus = UserStatus.Offline,
        displayName: String = id,
        lastActivity: String = "",
    ) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = displayName,
        friendKey = "",
        id = id,
        imageUrl = "",
        isFriend = true,
        lastActivity = lastActivity,
        lastLogin = "",
        lastPlatform = "standalonewindows",
        location = location,
        profilePicOverride = "",
        status = status,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}

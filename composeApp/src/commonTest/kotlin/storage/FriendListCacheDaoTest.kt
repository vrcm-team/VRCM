package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.friends.date.FriendData
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendListCacheDaoTest {
    @Test
    fun successfulEmptySnapshotIsStoredInsteadOfLookingLikeMissingCache() {
        val dao = FriendListCacheDao(MapSettings())
        assertNull(dao.load("usr_account"))

        dao.save("usr_account", FriendListCache(emptyList()))

        val restored = assertNotNull(dao.load("usr_account"))
        assertTrue(restored.friends.isEmpty())
    }

    @Test
    fun deletingLastFriendOverwritesPreviouslyCachedFriends() {
        val dao = FriendListCacheDao(MapSettings())
        dao.save("usr_account", FriendListCache(listOf(friend("usr_stale"))))

        dao.save("usr_account", FriendListCache(emptyList()))

        assertTrue(dao.load("usr_account")?.friends.orEmpty().isEmpty())
    }

    private fun friend(id: String) = FriendData(
        bio = null,
        currentAvatarImageUrl = "",
        currentAvatarThumbnailImageUrl = "",
        developerType = "none",
        displayName = id,
        friendKey = "",
        id = id,
        imageUrl = "",
        isFriend = true,
        lastLogin = "",
        lastPlatform = "standalonewindows",
        location = "offline",
        profilePicOverride = "",
        status = UserStatus.Offline,
        statusDescription = "",
        userIcon = "",
        pronouns = null,
    )
}

package io.github.vrcmteam.vrcm.storage

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.vrcmteam.vrcm.network.api.attributes.AgeVerificationStatus
import io.github.vrcmteam.vrcm.network.api.attributes.FriendRequestStatus
import io.github.vrcmteam.vrcm.network.api.attributes.UserState
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.storage.data.UserProfileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 这份缓存原先写在 Settings 里，单条可达 1.5 MB，在 iOS 上会撞到
 * NSUserDefaults 的 ~4 MB 上限。迁到 Room 后要保证：大 payload 能存取、
 * 每个账号有条数上限、账号之间互不影响。
 */
class RoomUserProfileCacheStoreTest {

    @Test
    fun storesPayloadsFarBeyondTheOldSettingsLimit() = withStore { store ->
        // 单条 ~2 MB：旧实现在 iOS 上到这个量级就已经报错了。
        val bulky = profileCache("Creator", bioPadding = 2_000_000)
        store.save("usr_owner", "usr_creator", bulky)

        val loaded = assertNotNull(store.load("usr_owner", "usr_creator"))
        assertEquals(bulky.user.bio, loaded.user.bio)
    }

    @Test
    fun keepsOnlyTheMostRecentlySavedProfilesPerOwner() = withStore(retained = 2) { store ->
        store.save("usr_owner", "usr_1", profileCache("One"))
        store.save("usr_owner", "usr_2", profileCache("Two"))
        store.save("usr_owner", "usr_3", profileCache("Three"))

        assertNull(store.load("usr_owner", "usr_1"))
        assertNotNull(store.load("usr_owner", "usr_2"))
        assertNotNull(store.load("usr_owner", "usr_3"))
    }

    @Test
    fun trimAndClearAreScopedToOneOwner() = withStore(retained = 1) { store ->
        store.save("usr_a", "usr_1", profileCache("A1"))
        store.save("usr_b", "usr_1", profileCache("B1"))
        // 多账号共用一张表：淘汰必须按账号分组，否则 A 逛得多会把 B 的缓存挤掉。
        store.save("usr_a", "usr_2", profileCache("A2"))

        assertNull(store.load("usr_a", "usr_1"))
        assertNotNull(store.load("usr_a", "usr_2"))
        assertNotNull(store.load("usr_b", "usr_1"))

        store.clearOwner("usr_a")

        assertNull(store.load("usr_a", "usr_2"))
        assertNotNull(store.load("usr_b", "usr_1"))
    }

    private fun withStore(
        retained: Int = 30,
        block: suspend (UserProfileCacheStore) -> Unit,
    ) = runTest {
        val database = Room.inMemoryDatabaseBuilder<VrcmDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            var clock = 0L
            block(
                RoomUserProfileCacheStore(
                    dao = database.cachedBlobDao(),
                    nowMillis = { ++clock },
                    retained = retained,
                ),
            )
        } finally {
            database.close()
        }
    }

    private fun profileCache(displayName: String, bioPadding: Int = 0) = UserProfileCache(
        user = UserData(
            ageVerificationStatus = AgeVerificationStatus.Verified,
            allowAvatarCopying = true,
            bio = "x".repeat(bioPadding),
            bioLinks = emptyList(),
            currentAvatarImageUrl = "",
            currentAvatarTags = emptyList(),
            currentAvatarThumbnailImageUrl = "",
            dateJoined = "2020-01-01",
            developerType = "none",
            displayName = displayName,
            friendKey = "",
            friendRequestStatus = FriendRequestStatus.Null,
            id = "usr_cached",
            instanceId = "",
            isFriend = true,
            lastActivity = "",
            lastLogin = "",
            lastPlatform = "standalonewindows",
            location = "",
            note = "",
            profilePicOverride = "",
            state = UserState.Online,
            status = UserStatus.Active,
            statusDescription = "",
            tags = emptyList(),
            travelingToInstance = "",
            travelingToLocation = "",
            travelingToWorld = "",
            userIcon = "",
            worldId = "",
            pronouns = null,
        ),
    )
}

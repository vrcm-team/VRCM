package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountCacheManagerTest {
    @Test
    fun clearingOneAccountDoesNotRemoveAnotherAccountsCaches() = runTest {
        val friendSettings = MapSettings()
        val profileSettings = MapSettings()
        val friendDao = FriendListCacheDao(friendSettings)
        val profileDao = UserProfileCacheDao(profileSettings)
        val manager = accountCacheManager(friendDao, profileDao)
        friendDao.save("usr_a", FriendListCache(emptyList()))
        friendDao.save("usr_b", FriendListCache(emptyList()))
        profileSettings.putString(profileKey("usr_a", "usr_profile"), "cached-a")
        profileSettings.putString(profileKey("usr_b", "usr_profile"), "cached-b")

        manager.clearAccount("usr_a")

        assertNull(friendDao.load("usr_a"))
        assertNotNull(friendDao.load("usr_b"))
        assertTrue(profileSettings.keys.none { it.contains(".usr_a.") })
        assertTrue(profileSettings.keys.any { it.contains(".usr_b.") })
    }

    @Test
    fun clearingAllRemovesBothCacheTypesForEveryAccount() = runTest {
        val friendSettings = MapSettings()
        val profileSettings = MapSettings()
        val friendDao = FriendListCacheDao(friendSettings)
        val profileDao = UserProfileCacheDao(profileSettings)
        val manager = accountCacheManager(friendDao, profileDao)
        friendDao.save("usr_a", FriendListCache(emptyList()))
        friendDao.save("usr_b", FriendListCache(emptyList()))
        profileSettings.putString(profileKey("usr_a", "usr_profile"), "cached-a")
        profileSettings.putString(profileKey("usr_b", "usr_profile"), "cached-b")

        manager.clearAll()

        assertTrue(friendSettings.keys.isEmpty())
        assertTrue(profileSettings.keys.isEmpty())
    }

    @Test
    fun queuedWriteCapturedBeforeAccountClearCannotRestoreDeletedCache() = runTest {
        val friendDao = FriendListCacheDao(MapSettings())
        val manager = accountCacheManager(friendDao, UserProfileCacheDao(MapSettings()))
        val staleToken = manager.captureWriteToken("usr_a")

        manager.clearAccount("usr_a")
        val saved = manager.saveFriendListIfCurrent(staleToken, FriendListCache(emptyList()))

        assertTrue(!saved)
        assertNull(friendDao.load("usr_a"))
    }

    private fun profileKey(ownerUserId: String, userId: String) =
        "${DaoKeys.UserProfileCache.KEY_PREFIX}.$ownerUserId.$userId"

    private fun accountCacheManager(
        friendDao: FriendListCacheDao,
        profileDao: UserProfileCacheDao,
    ) = AccountCacheManager(
        friendListCacheDao = friendDao,
        userProfileCacheDao = profileDao,
        friendActivityStore = NoOpFriendActivityCacheStore,
    )
}

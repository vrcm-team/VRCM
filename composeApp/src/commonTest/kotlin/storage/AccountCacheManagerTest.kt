package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.storage.data.FriendListCache
import io.github.vrcmteam.vrcm.storage.meetup.DecorationAssetType
import io.github.vrcmteam.vrcm.storage.meetup.MeetupAppearanceSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhoto
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhotoSource
import io.github.vrcmteam.vrcm.storage.meetup.defaultMeetupCardConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountCacheManagerTest {
    @Test
    fun mutationQueuedBehindAccountClearRejectsCapturedGeneration() = runTest {
        val manager = accountCacheManager(
            FriendListCacheDao(MapSettings()),
            UserProfileCacheDao(MapSettings()),
        )
        val token = manager.captureWriteToken("usr_a")
        val blockerStarted = CompletableDeferred<Unit>()
        val releaseBlocker = CompletableDeferred<Unit>()
        val blocker = launch {
            assertTrue(
                manager.mutateIfCurrent(token) {
                    blockerStarted.complete(Unit)
                    releaseBlocker.await()
                },
            )
        }
        blockerStarted.await()
        val clear = launch { manager.clearAccount("usr_a") }
        yield()
        var staleMutationRan = false
        var staleMutationCommitted = true
        val staleMutation = launch {
            staleMutationCommitted = manager.mutateIfCurrent(token) {
                staleMutationRan = true
            }
        }
        yield()

        releaseBlocker.complete(Unit)
        blocker.join()
        clear.join()
        staleMutation.join()

        assertFalse(staleMutationCommitted)
        assertFalse(staleMutationRan)
    }

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
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val configDao = MeetupCardConfigDao(MapSettings())
        val manager = accountCacheManager(friendDao, profileDao, configDao, assetStore)
        friendDao.save("usr_a", FriendListCache(emptyList()))
        friendDao.save("usr_b", FriendListCache(emptyList()))
        profileSettings.putString(profileKey("usr_a", "usr_profile"), "cached-a")
        profileSettings.putString(profileKey("usr_b", "usr_profile"), "cached-b")
        val photoBytes = "local-photo".encodeToByteArray()
        val photo = assetStore.writePhoto("usr_a", photoBytes, "png")
        val decorationBytes = "decoration".encodeToByteArray()
        val decoration = assetStore.writeDecoration(
            "inv_keep",
            DecorationAssetType.MainAnimation,
            decorationBytes,
        )
        configDao.save(
            defaultMeetupCardConfig("usr_a").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.LocalAlbum,
                    localAsset = photo,
                ),
                appearance = MeetupAppearanceSnapshot(iconFrameTemplateId = "inv_keep"),
            ),
        )

        manager.clearAll()

        assertTrue(friendSettings.keys.isEmpty())
        assertTrue(profileSettings.keys.isEmpty())
        assertNotNull(configDao.load("usr_a"))
        assertContentEquals(photoBytes, assetStore.read(photo))
        assertContentEquals(decorationBytes, assetStore.read(decoration))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun queuedWriteCapturedBeforeAccountClearCannotRestoreDeletedCache() = runTest {
        val friendDao = FriendListCacheDao(MapSettings())
        val manager = accountCacheManager(friendDao, UserProfileCacheDao(MapSettings()))
        val staleToken = manager.captureWriteToken("usr_a")
        assertTrue(manager.isCurrent(staleToken))

        manager.clearAccount("usr_a")
        val saved = manager.saveFriendListIfCurrent(staleToken, FriendListCache(emptyList()))

        assertTrue(!saved)
        assertFalse(manager.isCurrent(staleToken))
        assertNull(friendDao.load("usr_a"))
    }

    @Test
    fun clearingAccountRemovesOnlyItsMeetupDataAndPrunesUnreferencedDecorations() = runTest {
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val configDao = MeetupCardConfigDao(MapSettings())
        val manager = accountCacheManager(
            FriendListCacheDao(MapSettings()),
            UserProfileCacheDao(MapSettings()),
            configDao,
            assetStore,
        )
        val ownerAPhoto = assetStore.writePhoto("usr_a", "photo-a".encodeToByteArray(), "jpg")
        val ownerBPhotoBytes = "photo-b".encodeToByteArray()
        val ownerBPhoto = assetStore.writePhoto("usr_b", ownerBPhotoBytes, "png")
        val removedDecoration = assetStore.writeDecoration(
            "inv_remove",
            DecorationAssetType.MainAnimation,
            "remove".encodeToByteArray(),
        )
        val keptIconBytes = "keep-icon".encodeToByteArray()
        val keptIcon = assetStore.writeDecoration(
            "inv_icon",
            DecorationAssetType.MainAnimation,
            keptIconBytes,
        )
        val keptProfile = assetStore.writeDecoration(
            "inv_profile",
            DecorationAssetType.Base,
            "keep-profile".encodeToByteArray(),
        )
        val keptNameplate = assetStore.writeDecoration(
            "inv_nameplate",
            DecorationAssetType.Base,
            "keep-nameplate".encodeToByteArray(),
        )
        configDao.save(
            defaultMeetupCardConfig("usr_a").copy(
                photo = MeetupPhoto(localAsset = ownerAPhoto),
                appearance = MeetupAppearanceSnapshot(iconFrameTemplateId = "inv_remove"),
            ),
        )
        configDao.save(
            defaultMeetupCardConfig("usr_b").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.LocalAlbum,
                    localAsset = ownerBPhoto,
                ),
                appearance = MeetupAppearanceSnapshot(
                    iconFrameTemplateId = " inv_icon ",
                    profileEffectTemplateId = "inv_profile",
                    nameplateEffectTemplateId = "inv_nameplate",
                ),
            ),
        )

        manager.clearAccount("usr_a")

        assertNull(configDao.load("usr_a"))
        assertNotNull(configDao.load("usr_b"))
        assertFailsWith<IOException> { assetStore.read(ownerAPhoto) }
        assertContentEquals(ownerBPhotoBytes, assetStore.read(ownerBPhoto))
        assertFailsWith<IOException> { assetStore.read(removedDecoration) }
        assertContentEquals(keptIconBytes, assetStore.read(keptIcon))
        assertContentEquals("keep-profile".encodeToByteArray(), assetStore.read(keptProfile))
        assertContentEquals("keep-nameplate".encodeToByteArray(), assetStore.read(keptNameplate))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun failedAccountFileCleanupStillInvalidatesCapturedGeneration() = runTest {
        val fileSystem = FakeFileSystem()
        val failingFileSystem = object : ForwardingFileSystem(fileSystem) {
            override fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) {
                if (fileOrDirectory.toString().contains("/accounts/usr_a")) {
                    throw IOException("delete failed")
                }
                super.deleteRecursively(fileOrDirectory, mustExist)
            }
        }
        val manager = accountCacheManager(
            FriendListCacheDao(MapSettings()),
            UserProfileCacheDao(MapSettings()),
            MeetupCardConfigDao(MapSettings()),
            MeetupCardAssetStore(failingFileSystem, "/meetup-assets".toPath()),
        )
        val token = manager.captureWriteToken("usr_a")

        assertFailsWith<IOException> { manager.clearAccount("usr_a") }

        assertFalse(manager.isCurrent(token))
        fileSystem.checkNoOpenFiles()
    }

    private fun profileKey(ownerUserId: String, userId: String) =
        "${DaoKeys.UserProfileCache.KEY_PREFIX}.$ownerUserId.$userId"

    private fun accountCacheManager(
        friendDao: FriendListCacheDao,
        profileDao: UserProfileCacheDao,
        configDao: MeetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
        assetStore: MeetupCardAssetStore = MeetupCardAssetStore(
            FakeFileSystem(),
            "/meetup-assets".toPath(),
        ),
    ) = AccountCacheManager(
        friendListCacheDao = friendDao,
        userProfileCacheDao = profileDao,
        friendActivityStore = NoOpFriendActivityCacheStore,
        meetupCardConfigDao = configDao,
        meetupCardAssetStore = assetStore,
    )
}

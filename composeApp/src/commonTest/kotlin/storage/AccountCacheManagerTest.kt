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
import io.github.vrcmteam.vrcm.network.api.attributes.AgeVerificationStatus
import io.github.vrcmteam.vrcm.network.api.attributes.FriendRequestStatus
import io.github.vrcmteam.vrcm.network.api.attributes.UserState
import io.github.vrcmteam.vrcm.network.api.attributes.UserStatus
import io.github.vrcmteam.vrcm.network.api.users.data.UserData
import io.github.vrcmteam.vrcm.storage.data.UserProfileCache
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountCacheManagerTest {
    @Test
    fun mutationQueuedBehindAccountClearRejectsCapturedGeneration() = runTest {
        val manager = accountCacheManager(
            FriendListCacheDao(MapSettings()),
            InMemoryUserProfileCacheStore(),
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
        val friendDao = FriendListCacheDao(friendSettings)
        val profileStore = InMemoryUserProfileCacheStore()
        val manager = accountCacheManager(friendDao, profileStore)
        friendDao.save("usr_a", FriendListCache(emptyList()))
        friendDao.save("usr_b", FriendListCache(emptyList()))
        profileStore.save("usr_a", "usr_profile", profileCache("A"))
        profileStore.save("usr_b", "usr_profile", profileCache("B"))

        manager.clearAccount("usr_a")

        assertNull(friendDao.load("usr_a"))
        assertNotNull(friendDao.load("usr_b"))
        assertNull(profileStore.load("usr_a", "usr_profile"))
        assertNotNull(profileStore.load("usr_b", "usr_profile"))
    }

    @Test
    fun clearingAllRemovesBothCacheTypesForEveryAccount() = runTest {
        val friendSettings = MapSettings()
        val friendDao = FriendListCacheDao(friendSettings)
        val profileStore = InMemoryUserProfileCacheStore()
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val configDao = MeetupCardConfigDao(MapSettings())
        val manager = accountCacheManager(friendDao, profileStore, configDao, assetStore)
        friendDao.save("usr_a", FriendListCache(emptyList()))
        friendDao.save("usr_b", FriendListCache(emptyList()))
        profileStore.save("usr_a", "usr_profile", profileCache("A"))
        profileStore.save("usr_b", "usr_profile", profileCache("B"))
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
        assertNull(profileStore.load("usr_a", "usr_profile"))
        assertNull(profileStore.load("usr_b", "usr_profile"))
        assertNotNull(configDao.load("usr_a"))
        assertContentEquals(photoBytes, assetStore.read(photo))
        assertContentEquals(decorationBytes, assetStore.read(decoration))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun queuedWriteCapturedBeforeAccountClearCannotRestoreDeletedCache() = runTest {
        val friendDao = FriendListCacheDao(MapSettings())
        val manager = accountCacheManager(friendDao, InMemoryUserProfileCacheStore())
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
            InMemoryUserProfileCacheStore(),
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
            InMemoryUserProfileCacheStore(),
            MeetupCardConfigDao(MapSettings()),
            MeetupCardAssetStore(failingFileSystem, "/meetup-assets".toPath()),
        )
        val token = manager.captureWriteToken("usr_a")

        assertFailsWith<IOException> { manager.clearAccount("usr_a") }

        assertFalse(manager.isCurrent(token))
        fileSystem.checkNoOpenFiles()
    }

    private fun profileCache(displayName: String) = UserProfileCache(
        user = UserData(
            ageVerificationStatus = AgeVerificationStatus.Verified,
            allowAvatarCopying = true,
            bio = "",
            bioLinks = emptyList(),
            currentAvatarImageUrl = "",
            currentAvatarTags = emptyList(),
            currentAvatarThumbnailImageUrl = "",
            dateJoined = "2020-01-01",
            developerType = "none",
            displayName = displayName,
            friendKey = "",
            friendRequestStatus = FriendRequestStatus.Null,
            id = "usr_profile",
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

    private fun accountCacheManager(
        friendDao: FriendListCacheDao,
        profileStore: UserProfileCacheStore,
        configDao: MeetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
        assetStore: MeetupCardAssetStore = MeetupCardAssetStore(
            FakeFileSystem(),
            "/meetup-assets".toPath(),
        ),
    ) = AccountCacheManager(
        friendListCacheDao = friendDao,
        userProfileCacheStore = profileStore,
        friendActivityStore = NoOpFriendActivityCacheStore,
        meetupCardConfigDao = configDao,
        meetupCardAssetStore = assetStore,
    )
}

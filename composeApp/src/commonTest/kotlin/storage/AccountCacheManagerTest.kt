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
            InMemoryFriendListCacheStore(),
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
        val friendStore = InMemoryFriendListCacheStore()
        val profileStore = InMemoryUserProfileCacheStore()
        val favoriteStore = InMemoryFavoriteListCacheStore()
        val manager = accountCacheManager(
            friendStore,
            profileStore,
            favoriteStore = favoriteStore,
        )
        friendStore.save("usr_a", FriendListCache(emptyList()))
        friendStore.save("usr_b", FriendListCache(emptyList()))
        profileStore.save("usr_a", "usr_profile", profileCache("A"))
        profileStore.save("usr_b", "usr_profile", profileCache("B"))
        favoriteStore.saveWorlds("usr_a", emptyList())
        favoriteStore.saveAvatars("usr_b", emptyList())

        manager.clearAccount("usr_a")

        assertNull(friendStore.load("usr_a"))
        assertNotNull(friendStore.load("usr_b"))
        assertNull(profileStore.load("usr_a", "usr_profile"))
        assertNotNull(profileStore.load("usr_b", "usr_profile"))
        assertNull(favoriteStore.load("usr_a"))
        assertNotNull(favoriteStore.load("usr_b"))
    }

    @Test
    fun clearingAllRemovesBothCacheTypesForEveryAccount() = runTest {
        val friendStore = InMemoryFriendListCacheStore()
        val profileStore = InMemoryUserProfileCacheStore()
        val favoriteStore = InMemoryFavoriteListCacheStore()
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val configDao = MeetupCardConfigDao(MapSettings())
        val manager = accountCacheManager(
            friendStore,
            profileStore,
            configDao,
            assetStore,
            favoriteStore,
        )
        friendStore.save("usr_a", FriendListCache(emptyList()))
        friendStore.save("usr_b", FriendListCache(emptyList()))
        profileStore.save("usr_a", "usr_profile", profileCache("A"))
        profileStore.save("usr_b", "usr_profile", profileCache("B"))
        favoriteStore.saveWorlds("usr_a", emptyList())
        favoriteStore.saveAvatars("usr_b", emptyList())
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

        assertNull(friendStore.load("usr_a"))
        assertNull(friendStore.load("usr_b"))
        assertNull(profileStore.load("usr_a", "usr_profile"))
        assertNull(profileStore.load("usr_b", "usr_profile"))
        assertNull(favoriteStore.load("usr_a"))
        assertNull(favoriteStore.load("usr_b"))
        assertNotNull(configDao.load("usr_a"))
        assertContentEquals(photoBytes, assetStore.read(photo))
        assertContentEquals(decorationBytes, assetStore.read(decoration))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun queuedWriteCapturedBeforeAccountClearCannotRestoreDeletedCache() = runTest {
        val friendStore = InMemoryFriendListCacheStore()
        val manager = accountCacheManager(friendStore, InMemoryUserProfileCacheStore())
        val staleToken = manager.captureWriteToken("usr_a")
        assertTrue(manager.isCurrent(staleToken))

        manager.clearAccount("usr_a")
        val saved = manager.saveFriendListIfCurrent(staleToken, FriendListCache(emptyList()))

        assertTrue(!saved)
        assertFalse(manager.isCurrent(staleToken))
        assertNull(friendStore.load("usr_a"))
    }

    @Test
    fun clearingAccountRemovesOnlyItsMeetupDataAndPrunesUnreferencedDecorations() = runTest {
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val configDao = MeetupCardConfigDao(MapSettings())
        val manager = accountCacheManager(
            InMemoryFriendListCacheStore(),
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
            InMemoryFriendListCacheStore(),
            InMemoryUserProfileCacheStore(),
            MeetupCardConfigDao(MapSettings()),
            MeetupCardAssetStore(failingFileSystem, "/meetup-assets".toPath()),
        )
        val token = manager.captureWriteToken("usr_a")

        assertFailsWith<IOException> { manager.clearAccount("usr_a") }

        assertFalse(manager.isCurrent(token))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun leasesCapturedBeforeAnAccountClearAreRefused() = runTest {
        val manager = accountCacheManager(
            InMemoryFriendListCacheStore(),
            InMemoryUserProfileCacheStore(),
        )
        val staleToken = manager.captureWriteToken("usr_a")
        manager.clearAccount("usr_a")

        // 拿着过期 token 还能开租约的话，被清掉的账号又能往素材目录里写东西了。
        assertNull(manager.acquireDecorationLease(staleToken, setOf("inv_icon")))
        assertNull(manager.acquirePhotoArtifactLease(staleToken))
        assertNotNull(manager.acquirePhotoArtifactLease(manager.captureWriteToken("usr_a")))
    }

    @Test
    fun heldDecorationLeaseProtectsInFlightAssetsUntilItIsReleased() = runTest {
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val configDao = MeetupCardConfigDao(MapSettings())
        val manager = accountCacheManager(
            InMemoryFriendListCacheStore(),
            InMemoryUserProfileCacheStore(),
            configDao,
            assetStore,
        )
        val inFlightBytes = "in-flight".encodeToByteArray()
        val inFlight = assetStore.writeDecoration(
            "inv_in_flight",
            DecorationAssetType.MainAnimation,
            inFlightBytes,
        )
        val token = manager.captureWriteToken("usr_a")
        val lease = assertNotNull(manager.acquireDecorationLease(token, setOf("inv_in_flight")))

        // 素材还没写进任何配置，只有租约在护着它；此时清另一个账号不能把它剪掉。
        manager.clearAccount("usr_b")
        assertContentEquals(inFlightBytes, assetStore.read(inFlight))

        manager.releaseDecorationLease(lease)

        assertFailsWith<IOException> { assetStore.read(inFlight) }
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun releasingPhotoLeaseDeletesOnlyUnreferencedArtifacts() = runTest {
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val configDao = MeetupCardConfigDao(MapSettings())
        val manager = accountCacheManager(
            InMemoryFriendListCacheStore(),
            InMemoryUserProfileCacheStore(),
            configDao,
            assetStore,
        )
        val committedBytes = "committed".encodeToByteArray()
        val committed = assetStore.writePhoto("usr_a", committedBytes, "jpg")
        val discarded = assetStore.writePhoto("usr_a", "discarded".encodeToByteArray(), "png")
        val otherOwnerBytes = "other-owner".encodeToByteArray()
        val otherOwner = assetStore.writePhoto("usr_b", otherOwnerBytes, "jpg")
        configDao.save(
            defaultMeetupCardConfig("usr_a").copy(photo = MeetupPhoto(localAsset = committed)),
        )
        val lease = assertNotNull(
            manager.acquirePhotoArtifactLease(manager.captureWriteToken("usr_a")),
        )
        listOf(committed, discarded).forEach { manager.recordPhotoArtifact(lease, it) }

        manager.releasePhotoArtifactLease(lease)

        assertContentEquals(committedBytes, assetStore.read(committed))
        assertFailsWith<IOException> { assetStore.read(discarded) }
        assertContentEquals(otherOwnerBytes, assetStore.read(otherOwner))
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun concurrentPhotoLeaseForSameOwnerKeepsItsOwnArtifactAlive() = runTest {
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val manager = accountCacheManager(
            InMemoryFriendListCacheStore(),
            InMemoryUserProfileCacheStore(),
            MeetupCardConfigDao(MapSettings()),
            assetStore,
        )
        val sharedBytes = "shared".encodeToByteArray()
        val shared = assetStore.writePhoto("usr_a", sharedBytes, "jpg")
        val token = manager.captureWriteToken("usr_a")
        val first = assertNotNull(manager.acquirePhotoArtifactLease(token))
        val second = assertNotNull(manager.acquirePhotoArtifactLease(token))
        manager.recordPhotoArtifact(first, shared)
        manager.recordPhotoArtifact(second, shared)

        // 两次刷新写出了同一份内容寻址的照片；先结束的那次不能把还在用的文件删掉。
        manager.releasePhotoArtifactLease(first)
        assertContentEquals(sharedBytes, assetStore.read(shared))

        manager.releasePhotoArtifactLease(second)

        assertFailsWith<IOException> { assetStore.read(shared) }
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun recordingAnArtifactAfterReleaseFailsLoudly() = runTest {
        val fileSystem = FakeFileSystem()
        val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
        val manager = accountCacheManager(
            InMemoryFriendListCacheStore(),
            InMemoryUserProfileCacheStore(),
            MeetupCardConfigDao(MapSettings()),
            assetStore,
        )
        val photo = assetStore.writePhoto("usr_a", "photo".encodeToByteArray(), "jpg")
        val lease = assertNotNull(
            manager.acquirePhotoArtifactLease(manager.captureWriteToken("usr_a")),
        )
        manager.releasePhotoArtifactLease(lease)

        // 静默接受会让这份照片永远没人负责回收，不如当场炸出来。
        assertFailsWith<IllegalStateException> { manager.recordPhotoArtifact(lease, photo) }
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
        friendStore: FriendListCacheStore,
        profileStore: UserProfileCacheStore,
        configDao: MeetupCardConfigDao = MeetupCardConfigDao(MapSettings()),
        assetStore: MeetupCardAssetStore = MeetupCardAssetStore(
            FakeFileSystem(),
            "/meetup-assets".toPath(),
        ),
        favoriteStore: FavoriteListCacheStore = InMemoryFavoriteListCacheStore(),
    ) = AccountCacheManager(
        friendListCacheStore = friendStore,
        userProfileCacheStore = profileStore,
        friendActivityStore = NoOpFriendActivityCacheStore,
        meetupCardConfigDao = configDao,
        meetupCardAssetStore = assetStore,
        favoriteListCacheStore = favoriteStore,
    )
}

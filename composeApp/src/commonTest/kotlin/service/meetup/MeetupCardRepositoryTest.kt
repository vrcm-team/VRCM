package io.github.vrcmteam.vrcm.service.meetup

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateAsset
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateData
import io.github.vrcmteam.vrcm.network.api.inventory.data.InventoryTemplateMetadata
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.AnimatedWebpDecoder
import io.github.vrcmteam.vrcm.presentation.screens.meetup.animation.DecodedAnimation
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.FriendListCacheDao
import io.github.vrcmteam.vrcm.storage.NoOpFriendActivityCacheStore
import io.github.vrcmteam.vrcm.storage.UserProfileCacheDao
import io.github.vrcmteam.vrcm.storage.meetup.DecorationAssetType
import io.github.vrcmteam.vrcm.storage.meetup.DecorationTemplateCache
import io.github.vrcmteam.vrcm.storage.meetup.DecorationTemplateCacheDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupAppearanceSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfig
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfigDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardTemplate
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhoto
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhotoSource
import io.github.vrcmteam.vrcm.storage.meetup.MeetupProfileSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.defaultMeetupCardConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okio.IOException
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeetupCardRepositoryTest {
    @Test
    fun ensureDefaultQueuedBehindClearCannotRecreateConfig() = repositoryTest {
        val blocker = blockAccountMutations("usr_a")
        val clear = scope.launch { accountCacheManager.clearAccount("usr_a") }
        yield()
        val ensure = scope.async { repository.ensureDefault("usr_a") }
        yield()

        assertFalse(ensure.isCompleted)
        blocker.release()
        clear.join()
        ensure.await()

        assertNull(configDao.load("usr_a"))
    }

    @Test
    fun updateQueuedBehindClearCannotRecreateConfig() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val blocker = blockAccountMutations("usr_a")
        val clear = scope.launch { accountCacheManager.clearAccount("usr_a") }
        yield()
        val update = scope.async {
            repository.update("usr_a") { it.copy(shortText = "stale edit") }
        }
        yield()

        assertFalse(update.isCompleted)
        blocker.release()
        clear.join()
        update.await()

        assertNull(configDao.load("usr_a"))
    }

    @Test
    fun replacePhotoQueuedBehindClearDoesNotWriteAccountAsset() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val blocker = blockAccountMutations("usr_a")
        val clear = scope.launch { accountCacheManager.clearAccount("usr_a") }
        yield()
        val replace = scope.async {
            repository.replacePhoto(
                "usr_a",
                photoCandidate(
                    source = MeetupPhotoSource.LocalAlbum,
                    sourceId = "stale-photo",
                    sourceUrl = null,
                    fileName = "stale.jpg",
                    bytes = "stale-photo".encodeToByteArray(),
                ),
            )
        }
        yield()

        assertFalse(replace.isCompleted)
        blocker.release()
        clear.join()
        assertTrue(replace.await().isFailure)

        assertNull(configDao.load("usr_a"))
        assertFalse(fileSystem.exists("/meetup-assets/accounts/usr_a".toPath()))
    }

    @Test
    fun laterPhotoSelectionSupersedesEarlierQueuedSelection() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val sharedBytes = "shared-photo".encodeToByteArray()
        val blocker = blockAccountMutations("usr_a")
        val first = scope.async {
            repository.replacePhoto(
                "usr_a",
                photoCandidate(
                    source = MeetupPhotoSource.LocalAlbum,
                    sourceId = "first",
                    sourceUrl = null,
                    fileName = "first.jpg",
                    bytes = sharedBytes,
                ),
            )
        }
        yield()
        val second = scope.async {
            repository.replacePhoto(
                "usr_a",
                photoCandidate(
                    source = MeetupPhotoSource.VrchatGallery,
                    sourceId = "second",
                    sourceUrl = "https://cdn/second.jpg",
                    fileName = "second.jpg",
                    bytes = sharedBytes,
                ),
            )
        }
        yield()

        blocker.release()
        val firstResult = first.await()
        val secondResult = second.await()

        assertTrue(firstResult.isFailure)
        assertTrue(secondResult.isSuccess)
        val config = assertNotNull(configDao.load("usr_a"))
        assertEquals("second", config.photo.sourceId)
        assertContentEquals(
            sharedBytes,
            assetStore.read(assertNotNull(config.photo.localAsset)),
        )
    }

    @Test
    fun refreshQueuedBehindClearDoesNotStartRemoteRequests() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Stale Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        val blocker = blockAccountMutations("usr_a")
        val clear = scope.launch { accountCacheManager.clearAccount("usr_a") }
        yield()

        val refresh = repository.refresh("usr_a")
        yield()

        assertFalse(refresh.isCompleted)
        assertTrue(remote.profileRequests.isEmpty())
        blocker.release()
        clear.join()
        refresh.join()

        assertNull(configDao.load("usr_a"))
        assertTrue(remote.profileRequests.isEmpty())
    }

    @Test
    fun observeRestoresCachedPhotoModelWithoutStartingNetwork() = repositoryTest {
        val photo = assetStore.writePhoto(
            ownerId = "usr_a",
            bytes = "cached-photo".encodeToByteArray(),
            extension = "webp",
        )
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(localAsset = photo),
            ),
        )

        val state = repository.observe("usr_a").value

        assertEquals(assetStore.model(photo), state.photoModel)
        assertTrue(remote.imageRequests.isEmpty())
        assertTrue(decorationSource.requests.isEmpty())
    }

    @Test
    fun observeRestoresCachedDecorationsWithoutStartingNetwork() = repositoryTest {
        val base = assetStore.writeDecoration(
            templateId = "inv_cached",
            type = DecorationAssetType.Base,
            bytes = "cached-decoration".encodeToByteArray(),
        )
        decorationCacheDao.save(
            DecorationTemplateCache(
                templateId = "inv_cached",
                baseUrl = "https://cdn/cached.webp",
                baseAsset = base,
                gradientStart = "#112233",
                gradientEnd = "#445566",
            ),
        )
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                appearance = MeetupAppearanceSnapshot(iconFrameTemplateId = "inv_cached"),
            ),
        )

        val state = repository.observe("usr_a").value

        val decoration = assertNotNull(state.decorations[DecorationSlot.IconFrame])
        assertEquals(DecorationRenderMode.Static, decoration.mode)
        assertEquals(base, decoration.asset)
        assertEquals("#112233", decoration.gradientStart)
        assertEquals("#445566", decoration.gradientEnd)
        assertTrue(remote.imageRequests.isEmpty())
        assertTrue(decorationSource.requests.isEmpty())
    }

    @Test
    fun cachedCardIsEmittedBeforeBlockedNetworkCompletes() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val profileStarted = CompletableDeferred<Unit>()
        val releaseNetwork = CompletableDeferred<Unit>()
        remote.profileHandler = { ownerId ->
            profileStarted.complete(Unit)
            releaseNetwork.await()
            remoteProfile(ownerId, displayName = "Network Name")
        }
        remote.appearanceHandler = { ownerId ->
            releaseNetwork.await()
            MeetupRemoteAppearance(id = ownerId)
        }

        val initial = repository.observe("usr_a").value
        val refresh = repository.refresh("usr_a")
        profileStarted.await()

        assertEquals("Cached Name", initial.config.profile.displayName)
        assertFalse(initial.refreshing)
        assertEquals("Cached Name", repository.observe("usr_a").value.config.profile.displayName)
        assertTrue(repository.observe("usr_a").value.refreshing)

        releaseNetwork.complete(Unit)
        refresh.join()
    }

    @Test
    fun refreshFailureKeepsLastValidCardAndReportsFailed() = repositoryTest {
        val cachedDecoration = assetStore.writeDecoration(
            templateId = "inv_cached",
            type = DecorationAssetType.Base,
            bytes = "cached-decoration".encodeToByteArray(),
        )
        decorationCacheDao.save(
            DecorationTemplateCache(
                templateId = "inv_cached",
                baseAsset = cachedDecoration,
            ),
        )
        val cached = cachedConfig("usr_a", "Cached Name").copy(
            appearance = MeetupAppearanceSnapshot(iconFrameTemplateId = "inv_cached"),
        )
        configDao.save(cached)
        remote.profileHandler = { throw IOException("profile offline") }
        remote.appearanceHandler = { throw IOException("appearance offline") }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals(cached, state.config)
        assertFalse(state.refreshing)
        assertIs<MeetupRefreshResult.Failed>(state.lastRefresh)
        assertEquals(
            cachedDecoration,
            state.decorations[DecorationSlot.IconFrame]?.asset,
        )
    }

    @Test
    fun failedProfileRequestsStillCommitSuccessfullyRepairedRemotePhoto() = repositoryTest {
        val stale = assetStore.writePhoto(
            ownerId = "usr_a",
            bytes = "stale".encodeToByteArray(),
            extension = "jpg",
        )
        fileSystem.delete(assetStore.model(stale).toPath())
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.VrchatGallery,
                    sourceId = "prnt_cached",
                    sourceUrl = "https://cdn/recover.jpg",
                    localAsset = stale,
                ),
            ),
        )
        remote.profileHandler = { throw IOException("profile offline") }
        remote.appearanceHandler = { throw IOException("appearance offline") }
        remote.imageHandler = { "recovered-photo".encodeToByteArray() }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        val repaired = assertNotNull(state.config.photo.localAsset)
        assertTrue(repaired != stale)
        assertContentEquals("recovered-photo".encodeToByteArray(), assetStore.read(repaired))
        assertEquals(assetStore.model(repaired), state.photoModel)
        assertIs<MeetupRefreshResult.Failed>(state.lastRefresh)
    }

    @Test
    fun partialRefreshRepairsProfileBackgroundFallbackWithoutOverwritingGallery() = repositoryTest {
        val stale = assetStore.writePhoto(
            ownerId = "usr_a",
            bytes = "stale-background".encodeToByteArray(),
            extension = "jpg",
        )
        fileSystem.delete(assetStore.model(stale).toPath())
        val staleBackground = MeetupPhoto(
            source = MeetupPhotoSource.ProfileBackground,
            sourceUrl = "https://cdn/repair-background.jpg",
            localAsset = stale,
        )
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = staleBackground,
                profileBackgroundFallback = staleBackground,
            ),
        )
        val profileStarted = CompletableDeferred<Unit>()
        val releaseProfile = CompletableDeferred<Unit>()
        remote.profileHandler = {
            profileStarted.complete(Unit)
            releaseProfile.await()
            throw IOException("profile offline")
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        val repairedBytes = "repaired-background".encodeToByteArray()
        remote.imageHandler = { repairedBytes }

        val refresh = repository.refresh("usr_a")
        profileStarted.await()
        val galleryBytes = "current-gallery".encodeToByteArray()
        assertTrue(
            repository.replacePhoto(
                "usr_a",
                photoCandidate(
                    source = MeetupPhotoSource.VrchatGallery,
                    sourceId = "prnt_current",
                    sourceUrl = "https://cdn/current-gallery.jpg",
                    fileName = "current-gallery.jpg",
                    bytes = galleryBytes,
                ),
            ).isSuccess,
        )
        releaseProfile.complete(Unit)
        refresh.join()

        val state = repository.observe("usr_a").value
        assertEquals(MeetupPhotoSource.VrchatGallery, state.config.photo.source)
        assertEquals("prnt_current", state.config.photo.sourceId)
        assertContentEquals(
            galleryBytes,
            assetStore.read(assertNotNull(state.config.photo.localAsset)),
        )
        val repairedFallback = assertNotNull(state.config.profileBackgroundFallback)
        assertEquals(MeetupPhotoSource.ProfileBackground, repairedFallback.source)
        assertContentEquals(
            repairedBytes,
            assetStore.read(assertNotNull(repairedFallback.localAsset)),
        )
        assertIs<MeetupRefreshResult.Partial>(state.lastRefresh)
    }

    @Test
    fun failedRefreshSynchronizesRepairedProfileBackgroundFallback() = repositoryTest {
        val stale = assetStore.writePhoto(
            ownerId = "usr_a",
            bytes = "stale-background".encodeToByteArray(),
            extension = "webp",
        )
        fileSystem.delete(assetStore.model(stale).toPath())
        val staleBackground = MeetupPhoto(
            source = MeetupPhotoSource.ProfileBackground,
            sourceUrl = "https://cdn/repair-background.webp",
            localAsset = stale,
        )
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = staleBackground,
                profileBackgroundFallback = staleBackground,
            ),
        )
        remote.profileHandler = { throw IOException("profile offline") }
        remote.appearanceHandler = { throw IOException("appearance offline") }
        val repairedBytes = "repaired-background".encodeToByteArray()
        remote.imageHandler = { repairedBytes }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        val repairedPhoto = state.config.photo
        assertEquals(MeetupPhotoSource.ProfileBackground, repairedPhoto.source)
        assertEquals(repairedPhoto, state.config.profileBackgroundFallback)
        assertContentEquals(
            repairedBytes,
            assetStore.read(assertNotNull(repairedPhoto.localAsset)),
        )
        assertIs<MeetupRefreshResult.Failed>(state.lastRefresh)
    }

    @Test
    fun staleRefreshCleanupDoesNotDeleteCurrentPhotoWithSameContentAddress() = repositoryTest {
        val stale = assetStore.writePhoto(
            ownerId = "usr_a",
            bytes = "stale".encodeToByteArray(),
            extension = "jpg",
        )
        fileSystem.delete(assetStore.model(stale).toPath())
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.VrchatGallery,
                    sourceId = "prnt_stale",
                    sourceUrl = "https://cdn/shared.jpg",
                    localAsset = stale,
                ),
            ),
        )
        val profileStarted = CompletableDeferred<Unit>()
        val releaseProfile = CompletableDeferred<Unit>()
        remote.profileHandler = {
            profileStarted.complete(Unit)
            releaseProfile.await()
            throw IOException("profile offline")
        }
        remote.appearanceHandler = { throw IOException("appearance offline") }
        val sharedBytes = "shared-photo".encodeToByteArray()
        remote.imageHandler = { sharedBytes }

        val refresh = repository.refresh("usr_a")
        profileStarted.await()
        assertTrue(
            repository.replacePhoto(
                "usr_a",
                photoCandidate(
                    source = MeetupPhotoSource.LocalAlbum,
                    sourceId = "current",
                    sourceUrl = null,
                    fileName = "current.jpg",
                    bytes = sharedBytes,
                ),
            ).isSuccess,
        )
        releaseProfile.complete(Unit)
        refresh.join()

        val state = repository.observe("usr_a").value
        val current = assertNotNull(state.config.photo.localAsset)
        assertEquals("current", state.config.photo.sourceId)
        assertContentEquals(sharedBytes, assetStore.read(current))
        assertEquals(assetStore.model(current), state.photoModel)
        assertIs<MeetupRefreshResult.Failed>(state.lastRefresh)
    }

    @Test
    fun normalRefreshDeletesIgnoredCurrentSourceRepair() = repositoryTest {
        val stale = assetStore.writePhoto(
            ownerId = "usr_a",
            bytes = "stale-gallery".encodeToByteArray(),
            extension = "jpg",
        )
        fileSystem.delete(assetStore.model(stale).toPath())
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.VrchatGallery,
                    sourceId = "prnt_stale",
                    sourceUrl = "https://cdn/stale-gallery.jpg",
                    localAsset = stale,
                ),
            ),
        )
        val profileStarted = CompletableDeferred<Unit>()
        val releaseProfile = CompletableDeferred<Unit>()
        remote.profileHandler = { ownerId ->
            profileStarted.complete(Unit)
            releaseProfile.await()
            remoteProfile(ownerId, "Network Name")
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        remote.imageHandler = { "ignored-repair".encodeToByteArray() }

        val refresh = repository.refresh("usr_a")
        profileStarted.await()
        val currentBytes = "current-local".encodeToByteArray()
        assertTrue(
            repository.replacePhoto(
                "usr_a",
                photoCandidate(
                    source = MeetupPhotoSource.LocalAlbum,
                    sourceId = "current",
                    sourceUrl = null,
                    fileName = "current.jpg",
                    bytes = currentBytes,
                ),
            ).isSuccess,
        )
        releaseProfile.complete(Unit)
        refresh.join()

        val state = repository.observe("usr_a").value
        val current = assertNotNull(state.config.photo.localAsset)
        assertEquals("current", state.config.photo.sourceId)
        assertContentEquals(currentBytes, assetStore.read(current))
        assertEquals(
            listOf(current.relativePath.substringAfterLast('/')),
            photoFiles("usr_a").map { it.name },
        )
    }

    @Test
    fun newerRefreshDeletesCurrentSourceRepairWrittenByCancelledRefresh() = repositoryTest {
        val stale = assetStore.writePhoto(
            ownerId = "usr_a",
            bytes = "stale-gallery".encodeToByteArray(),
            extension = "jpg",
        )
        fileSystem.delete(assetStore.model(stale).toPath())
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.VrchatGallery,
                    sourceUrl = "https://cdn/stale-gallery.jpg",
                    localAsset = stale,
                ),
            ),
        )
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        var appearanceRequest = 0
        remote.appearanceHandler = { ownerId ->
            appearanceRequest++
            MeetupRemoteAppearance(
                id = ownerId,
                iconFrame = "inv_block_repair".takeIf { appearanceRequest == 1 },
            )
        }
        decorationSource.handler = { templateId ->
            InventoryTemplateData(
                id = templateId,
                metadata = InventoryTemplateMetadata(
                    assets = listOf(
                        InventoryTemplateAsset(
                            type = "base",
                            url = "https://cdn/$templateId.webp",
                        ),
                    ),
                ),
            )
        }
        val decorationLoadStarted = CompletableDeferred<Unit>()
        val neverCompleteDecoration = CompletableDeferred<ByteArray>()
        decorationBytesLoader.handler = { _, _ ->
            decorationLoadStarted.complete(Unit)
            neverCompleteDecoration.await()
        }
        var imageRequest = 0
        remote.imageHandler = {
            imageRequest++
            if (imageRequest == 1) {
                "cancelled-repair".encodeToByteArray()
            } else {
                throw IOException("new refresh photo offline")
            }
        }

        val older = repository.refresh("usr_a")
        decorationLoadStarted.await()
        assertEquals(1, photoFiles("usr_a").size)
        val newer = repository.refresh("usr_a")
        newer.join()
        older.join()

        assertTrue(photoFiles("usr_a").isEmpty())
    }

    @Test
    fun newerRefreshDeletesBackgroundWrittenButNotCommittedByCancelledRefresh() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        var profileRequest = 0
        remote.profileHandler = { ownerId ->
            profileRequest++
            remoteProfile(ownerId, "Network Name").copy(
                profileBackgroundUrl = "https://cdn/old-background.jpg"
                    .takeIf { profileRequest == 1 }
                    .orEmpty(),
            )
        }
        var appearanceRequest = 0
        remote.appearanceHandler = { ownerId ->
            appearanceRequest++
            MeetupRemoteAppearance(
                id = ownerId,
                iconFrame = "inv_block_background".takeIf { appearanceRequest == 1 },
            )
        }
        decorationSource.handler = { templateId ->
            InventoryTemplateData(
                id = templateId,
                metadata = InventoryTemplateMetadata(
                    assets = listOf(
                        InventoryTemplateAsset(
                            type = "base",
                            url = "https://cdn/$templateId.webp",
                        ),
                    ),
                ),
            )
        }
        val decorationLoadStarted = CompletableDeferred<Unit>()
        val neverCompleteDecoration = CompletableDeferred<ByteArray>()
        decorationBytesLoader.handler = { _, _ ->
            decorationLoadStarted.complete(Unit)
            neverCompleteDecoration.await()
        }
        remote.imageHandler = { "cancelled-background".encodeToByteArray() }

        val older = repository.refresh("usr_a")
        decorationLoadStarted.await()
        assertEquals(1, photoFiles("usr_a").size)
        val newer = repository.refresh("usr_a")
        newer.join()
        older.join()

        assertNull(repository.observe("usr_a").value.config.profileBackgroundFallback)
        assertTrue(photoFiles("usr_a").isEmpty())
    }

    @Test
    fun localEditDuringRefreshIsMergedWithNetworkProfile() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val profileStarted = CompletableDeferred<Unit>()
        val releaseProfile = CompletableDeferred<Unit>()
        remote.profileHandler = { ownerId ->
            profileStarted.complete(Unit)
            releaseProfile.await()
            remoteProfile(ownerId, displayName = "Network Name")
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        val refresh = repository.refresh("usr_a")
        profileStarted.await()
        repository.update("usr_a") { config ->
            config.copy(shortText = "local edit", showShortText = true)
        }
        releaseProfile.complete(Unit)
        refresh.join()

        val config = repository.observe("usr_a").value.config
        assertEquals("local edit", config.shortText)
        assertTrue(config.showShortText)
        assertEquals("Network Name", config.profile.displayName)
    }

    @Test
    fun slowerOlderRefreshCannotOverwriteNewerRefresh() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var profileRequest = 0
        remote.profileHandler = { ownerId ->
            profileRequest++
            if (profileRequest == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
                remoteProfile(ownerId, "Older Name")
            } else {
                remoteProfile(ownerId, "Newer Name")
            }
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        val older = repository.refresh("usr_a")
        firstStarted.await()
        val newer = repository.refresh("usr_a")
        newer.join()
        releaseFirst.complete(Unit)
        older.join()

        val state = repository.observe("usr_a").value
        assertEquals("Newer Name", state.config.profile.displayName)
        assertFalse(state.refreshing)
    }

    @Test
    fun cancelledOlderRefreshCannotClearNewerRefreshingState() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val neverCompleteFirst = CompletableDeferred<MeetupRemoteProfile>()
        var profileRequest = 0
        remote.profileHandler = { ownerId ->
            profileRequest++
            if (profileRequest == 1) {
                firstStarted.complete(Unit)
                neverCompleteFirst.await()
            } else {
                secondStarted.complete(Unit)
                releaseSecond.await()
                remoteProfile(ownerId, "Newer Name")
            }
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        val older = repository.refresh("usr_a")
        firstStarted.await()
        val newer = repository.refresh("usr_a")
        secondStarted.await()
        older.join()

        assertTrue(repository.observe("usr_a").value.refreshing)
        releaseSecond.complete(Unit)
        newer.join()
        assertFalse(repository.observe("usr_a").value.refreshing)
    }

    @Test
    fun decorationsAreResolvedForAppearanceActuallyCommitted() = repositoryTest {
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                appearance = MeetupAppearanceSnapshot(iconFrameTemplateId = "inv_old"),
            ),
        )
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        val oldResolutionStarted = CompletableDeferred<Unit>()
        val releaseOldResolution = CompletableDeferred<Unit>()
        decorationSource.handler = { templateId ->
            if (templateId == "inv_old") {
                oldResolutionStarted.complete(Unit)
                releaseOldResolution.await()
            }
            InventoryTemplateData(
                id = templateId,
                metadata = InventoryTemplateMetadata(),
            )
        }

        val refresh = repository.refresh("usr_a")
        oldResolutionStarted.await()
        repository.update("usr_a") { config ->
            config.copy(
                appearance = config.appearance.copy(iconFrameTemplateId = "inv_new"),
            )
        }
        releaseOldResolution.complete(Unit)
        refresh.join()

        val state = repository.observe("usr_a").value
        assertEquals("inv_new", state.config.appearance.iconFrameTemplateId)
        assertEquals("inv_new", state.decorations[DecorationSlot.IconFrame]?.templateId)
        assertTrue("inv_new" in decorationSource.requests)
    }

    @Test
    fun accountGenerationRejectsLateRefresh() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val profileStarted = CompletableDeferred<Unit>()
        val releaseNetwork = CompletableDeferred<Unit>()
        remote.profileHandler = { ownerId ->
            profileStarted.complete(Unit)
            releaseNetwork.await()
            remoteProfile(ownerId, displayName = "Stale Name")
        }
        remote.appearanceHandler = { ownerId ->
            releaseNetwork.await()
            MeetupRemoteAppearance(id = ownerId)
        }

        val refresh = repository.refresh("usr_a")
        profileStarted.await()
        accountCacheManager.clearAccount("usr_a")
        releaseNetwork.complete(Unit)
        refresh.join()

        assertNull(configDao.load("usr_a"))
    }

    @Test
    fun accountClearPreventsLateBackgroundDownloadFromRecreatingAssets() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        val imageStarted = CompletableDeferred<Unit>()
        val releaseImage = CompletableDeferred<Unit>()
        remote.profileHandler = { ownerId ->
            remoteProfile(ownerId, "Network Name").copy(
                profileBackgroundUrl = "https://cdn/late.jpg",
            )
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        remote.imageHandler = {
            imageStarted.complete(Unit)
            releaseImage.await()
            "late-background".encodeToByteArray()
        }

        val refresh = repository.refresh("usr_a")
        imageStarted.await()
        accountCacheManager.clearAccount("usr_a")
        releaseImage.complete(Unit)
        refresh.join()

        assertNull(configDao.load("usr_a"))
        assertFalse(fileSystem.exists("/meetup-assets/accounts/usr_a".toPath()))
    }

    @Test
    fun retainedStateFlowIsResetWhenAccountIsCleared() = repositoryTest {
        val photo = assetStore.writePhoto(
            ownerId = "usr_a",
            bytes = "private-photo".encodeToByteArray(),
            extension = "jpg",
        )
        val decoration = assetStore.writeDecoration(
            templateId = "inv_private",
            type = DecorationAssetType.Base,
            bytes = "private-decoration".encodeToByteArray(),
        )
        decorationCacheDao.save(
            DecorationTemplateCache(
                templateId = "inv_private",
                baseAsset = decoration,
            ),
        )
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(localAsset = photo),
                appearance = MeetupAppearanceSnapshot(iconFrameTemplateId = "inv_private"),
            ),
        )
        val retained = repository.observe("usr_a")
        assertNotNull(retained.value.photoModel)
        assertTrue(retained.value.decorations.isNotEmpty())

        accountCacheManager.clearAccount("usr_a")

        assertEquals("Current Display", retained.value.config.profile.displayName)
        assertNull(retained.value.photoModel)
        assertTrue(retained.value.decorations.isEmpty())
        assertFalse(retained.value.refreshing)
        assertEquals(MeetupRefreshResult.NotStarted, retained.value.lastRefresh)
    }

    @Test
    fun delayedInvalidationCannotResetStateWrittenByNewGeneration() = repositoryTest {
        val notificationStarted = CompletableDeferred<Unit>()
        val releaseNotification = CompletableDeferred<Unit>()
        accountCacheManager.addInvalidationListener { invalidation ->
            if (invalidation.ownerId == "usr_a") {
                notificationStarted.complete(Unit)
                releaseNotification.await()
            }
        }
        val retained = repository.observe("usr_a")
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "New Generation") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        val clear = scope.launch { accountCacheManager.clearAccount("usr_a") }
        notificationStarted.await()
        // 清账号后配置不存在；新代状态由显式 ensureDefault 重新建档后刷新产生。
        repository.ensureDefault("usr_a")
        val refresh = repository.refresh("usr_a")
        refresh.join()
        assertEquals("New Generation", retained.value.config.profile.displayName)
        assertEquals(MeetupRefreshResult.Success, retained.value.lastRefresh)

        releaseNotification.complete(Unit)
        clear.join()

        assertEquals("New Generation", retained.value.config.profile.displayName)
        assertEquals(MeetupRefreshResult.Success, retained.value.lastRefresh)
    }

    @Test
    fun clearAllRetainsMeetupAssetsAndRebuildsRetainedStateFlow() = repositoryTest {
        val photoBytes = "retained-photo".encodeToByteArray()
        val photo = assetStore.writePhoto("usr_a", photoBytes, "jpg")
        val decorationBytes = "retained-decoration".encodeToByteArray()
        val decoration = assetStore.writeDecoration(
            templateId = "inv_retained",
            type = DecorationAssetType.Base,
            bytes = decorationBytes,
        )
        decorationCacheDao.save(
            DecorationTemplateCache(
                templateId = "inv_retained",
                baseAsset = decoration,
            ),
        )
        val cached = cachedConfig("usr_a", "Cached Name").copy(
            photo = MeetupPhoto(localAsset = photo),
            appearance = MeetupAppearanceSnapshot(iconFrameTemplateId = "inv_retained"),
        )
        configDao.save(cached)
        val retained = repository.observe("usr_a")

        accountCacheManager.clearAll()

        assertEquals(cached, retained.value.config)
        assertEquals(assetStore.model(photo), retained.value.photoModel)
        assertEquals(decoration, retained.value.decorations[DecorationSlot.IconFrame]?.asset)
        assertContentEquals(photoBytes, assetStore.read(photo))
        assertContentEquals(decorationBytes, assetStore.read(decoration))
        assertEquals(MeetupRefreshResult.NotStarted, retained.value.lastRefresh)
    }

    @Test
    fun crossAccountClearKeepsDecorationLeasedUntilOtherAccountCommits() = repositoryTest {
        configDao.save(cachedConfig("usr_b", "Cached B"))
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network B") }
        remote.appearanceHandler = { ownerId ->
            MeetupRemoteAppearance(id = ownerId, iconFrame = "inv_in_flight")
        }
        decorationSource.handler = { templateId ->
            InventoryTemplateData(
                id = templateId,
                metadata = InventoryTemplateMetadata(
                    assets = listOf(
                        InventoryTemplateAsset(
                            type = "base",
                            url = "https://cdn/$templateId.webp",
                        ),
                    ),
                ),
            )
        }
        val decorationLoadStarted = CompletableDeferred<Unit>()
        val releaseDecorationLoad = CompletableDeferred<Unit>()
        val decorationBytes = "in-flight-decoration".encodeToByteArray()
        decorationBytesLoader.handler = { _, _ ->
            decorationLoadStarted.complete(Unit)
            releaseDecorationLoad.await()
            decorationBytes
        }

        val refresh = repository.refresh("usr_b")
        decorationLoadStarted.await()
        val blocker = blockAccountMutations("usr_gate")
        val clear = scope.launch { accountCacheManager.clearAccount("usr_a") }
        yield()
        releaseDecorationLoad.complete(Unit)
        awaitCondition {
            decorationCacheDao.load("inv_in_flight")?.baseAsset != null
        }
        blocker.release()
        clear.join()
        refresh.join()

        val state = repository.observe("usr_b").value
        val decoration = assertNotNull(state.decorations[DecorationSlot.IconFrame])
        assertEquals(DecorationRenderMode.Static, decoration.mode)
        assertContentEquals(decorationBytes, assetStore.read(assertNotNull(decoration.asset)))
        assertEquals("inv_in_flight", configDao.load("usr_b")?.appearance?.iconFrameTemplateId)
    }

    @Test
    fun accountClearPrunesDecorationWrittenByLateStaleResolver() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId ->
            MeetupRemoteAppearance(id = ownerId, iconFrame = "inv_stale")
        }
        decorationSource.handler = { templateId ->
            InventoryTemplateData(
                id = templateId,
                metadata = InventoryTemplateMetadata(
                    assets = listOf(
                        InventoryTemplateAsset(
                            type = "base",
                            url = "https://cdn/$templateId.webp",
                        ),
                    ),
                ),
            )
        }
        val decorationLoadStarted = CompletableDeferred<Unit>()
        val releaseDecorationLoad = CompletableDeferred<Unit>()
        decorationBytesLoader.handler = { _, _ ->
            decorationLoadStarted.complete(Unit)
            releaseDecorationLoad.await()
            "stale-decoration".encodeToByteArray()
        }

        val refresh = repository.refresh("usr_a")
        decorationLoadStarted.await()
        accountCacheManager.clearAccount("usr_a")
        releaseDecorationLoad.complete(Unit)
        refresh.join()

        assertNull(configDao.load("usr_a"))
        assertFalse(fileSystem.exists("/meetup-assets/decorations/inv_stale".toPath()))
    }

    @Test
    fun mismatchedProfileResponseKeepsCachedProfileAndReportsPartial() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        remote.profileHandler = { remoteProfile("usr_other", "Untrusted Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals("Cached Name", state.config.profile.displayName)
        assertEquals(
            setOf("profile"),
            assertIs<MeetupRefreshResult.Partial>(state.lastRefresh).failedParts,
        )
    }

    @Test
    fun blankProfileResponseIdIsRejected() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        remote.profileHandler = { remoteProfile("", "Untrusted Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals("Cached Name", state.config.profile.displayName)
        assertEquals(
            setOf("profile"),
            assertIs<MeetupRefreshResult.Partial>(state.lastRefresh).failedParts,
        )
    }

    @Test
    fun mismatchedAppearanceResponseIsIgnoredAsOneFailedPart() = repositoryTest {
        val cachedAppearance = MeetupAppearanceSnapshot(
            iconFrameTemplateId = "inv_old_icon",
            profileEffectTemplateId = "inv_old_profile",
            nameplateEffectTemplateId = "inv_old_nameplate",
        )
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(appearance = cachedAppearance),
        )
        listOf("inv_old_icon", "inv_old_profile", "inv_old_nameplate").forEach { templateId ->
            val asset = assetStore.writeDecoration(
                templateId = templateId,
                type = DecorationAssetType.Base,
                bytes = "decoration-$templateId".encodeToByteArray(),
            )
            decorationCacheDao.save(
                DecorationTemplateCache(
                    templateId = templateId,
                    baseUrl = "https://cdn/$templateId.webp",
                    baseAsset = asset,
                ),
            )
        }
        decorationSource.handler = { throw IOException("decoration metadata offline") }
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = {
            MeetupRemoteAppearance(
                id = "usr_other",
                iconFrame = "inv_untrusted_icon",
                profileEffect = "inv_untrusted_profile",
                nameplateEffect = "inv_untrusted_nameplate",
            )
        }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals(cachedAppearance, state.config.appearance)
        val refresh = assertIs<MeetupRefreshResult.Partial>(state.lastRefresh)
        assertEquals(setOf("appearance"), refresh.failedParts)
        assertEquals(
            setOf("inv_old_icon", "inv_old_profile", "inv_old_nameplate"),
            decorationSource.requests.toSet(),
        )
        assertEquals("inv_old_icon", state.decorations[DecorationSlot.IconFrame]?.templateId)
        assertEquals("inv_old_profile", state.decorations[DecorationSlot.ProfileEffect]?.templateId)
        assertEquals("inv_old_nameplate", state.decorations[DecorationSlot.NameplateEffect]?.templateId)
    }

    @Test
    fun blankAppearanceResponseIdIsRejected() = repositoryTest {
        val cachedAppearance = MeetupAppearanceSnapshot(iconFrameTemplateId = "inv_cached")
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(appearance = cachedAppearance),
        )
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = {
            MeetupRemoteAppearance(id = "", iconFrame = "inv_untrusted")
        }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals(cachedAppearance, state.config.appearance)
        assertTrue(
            "appearance" in assertIs<MeetupRefreshResult.Partial>(state.lastRefresh).failedParts,
        )
    }

    @Test
    fun nullAppearanceFieldsArePreservedAndExplicitEmptyFieldClearsSlot() = repositoryTest {
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                appearance = MeetupAppearanceSnapshot(
                    iconFrameTemplateId = "inv_keep",
                    profileEffectTemplateId = "inv_clear",
                    nameplateEffectTemplateId = "inv_old",
                ),
            ),
        )
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId ->
            MeetupRemoteAppearance(
                id = ownerId,
                iconFrame = null,
                profileEffect = "   ",
                nameplateEffect = " inv_new ",
            )
        }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals("inv_keep", state.config.appearance.iconFrameTemplateId)
        assertEquals("", state.config.appearance.profileEffectTemplateId)
        assertEquals("inv_new", state.config.appearance.nameplateEffectTemplateId)
        assertEquals(setOf("inv_keep", "inv_new"), decorationSource.requests.toSet())
        assertTrue(DecorationSlot.ProfileEffect !in state.decorations)
    }

    @Test
    fun unavailableEquippedDecorationReportsPartialWithoutBlockingProfile() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId ->
            MeetupRemoteAppearance(id = ownerId, iconFrame = "inv_unavailable")
        }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals("Network Name", state.config.profile.displayName)
        assertEquals(
            DecorationRenderMode.Unavailable,
            state.decorations[DecorationSlot.IconFrame]?.mode,
        )
        assertTrue(
            "decorations" in assertIs<MeetupRefreshResult.Partial>(state.lastRefresh).failedParts,
        )
    }

    @Test
    fun blankRemoteDisplayNameDoesNotReplaceCachedDisplayName() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "   ") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals("Cached Name", state.config.profile.displayName)
        assertEquals(MeetupRefreshResult.Success, state.lastRefresh)
    }

    @Test
    fun localAlbumPhotoIsNeverOverwrittenByProfileBackgroundRefresh() = repositoryTest {
        val localBytes = "local-photo".encodeToByteArray()
        val localRef = assetStore.writePhoto("usr_a", localBytes, "jpg")
        val localPhoto = MeetupPhoto(
            source = MeetupPhotoSource.LocalAlbum,
            sourceId = "album-photo",
            localAsset = localRef,
            width = 1200,
            height = 1600,
        )
        configDao.save(cachedConfig("usr_a", "Cached Name").copy(photo = localPhoto))
        remote.profileHandler = { ownerId ->
            remoteProfile(ownerId, "Network Name").copy(
                profileBackgroundUrl = "https://cdn/new-background.png?token=safe",
            )
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        remote.imageHandler = { "network-background".encodeToByteArray() }

        repository.refresh("usr_a").join()

        val config = repository.observe("usr_a").value.config
        assertEquals(localPhoto, config.photo)
        assertEquals(
            "https://cdn/new-background.png?token=safe",
            config.profileBackgroundFallback?.sourceUrl,
        )
        assertTrue(
            assertNotNull(config.profileBackgroundFallback?.localAsset)
                .relativePath
                .endsWith(".png"),
        )
        assertEquals(assetStore.model(localRef), repository.observe("usr_a").value.photoModel)
    }

    @Test
    fun missingRemotePhotoIsDownloadedAgainAndMarksRefreshPartial() = repositoryTest {
        val staleRef = assetStore.writePhoto(
            "usr_a",
            "stale-gallery".encodeToByteArray(),
            "jpg",
        )
        fileSystem.delete(assetStore.model(staleRef).toPath())
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.VrchatGallery,
                    sourceId = "prnt_remote",
                    sourceUrl = "https://cdn/gallery.jpeg",
                    localAsset = staleRef,
                ),
            ),
        )
        val freshBytes = "fresh-gallery".encodeToByteArray()
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        remote.imageHandler = { freshBytes }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        val newAsset = assertNotNull(state.config.photo.localAsset)
        assertTrue(newAsset != staleRef)
        assertContentEquals(freshBytes, assetStore.read(newAsset))
        assertEquals(assetStore.model(newAsset), state.photoModel)
        assertTrue("photo" in assertIs<MeetupRefreshResult.Partial>(state.lastRefresh).failedParts)
        assertEquals(listOf("https://cdn/gallery.jpeg"), remote.imageRequests)
    }

    @Test
    fun brokenCurrentPhotoUsesReadableFallbackAndReportsPartial() = repositoryTest {
        val missing = assetStore.writePhoto(
            "usr_a",
            "missing-local".encodeToByteArray(),
            "png",
        )
        fileSystem.delete(assetStore.model(missing).toPath())
        val fallbackBytes = "fallback-background".encodeToByteArray()
        val fallbackAsset = assetStore.writePhoto("usr_a", fallbackBytes, "webp")
        val brokenPhoto = MeetupPhoto(
            source = MeetupPhotoSource.LocalAlbum,
            localAsset = missing,
        )
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = brokenPhoto,
                profileBackgroundFallback = MeetupPhoto(
                    source = MeetupPhotoSource.ProfileBackground,
                    sourceUrl = "https://cdn/fallback.webp",
                    localAsset = fallbackAsset,
                ),
            ),
        )
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals(brokenPhoto, state.config.photo)
        assertEquals(assetStore.model(fallbackAsset), state.photoModel)
        assertTrue("photo" in assertIs<MeetupRefreshResult.Partial>(state.lastRefresh).failedParts)
        assertTrue(remote.imageRequests.isEmpty())
    }

    @Test
    fun exhaustedPhotoChainFallsBackToThemeBackgroundInsteadOfDeadPath() = repositoryTest {
        val missing = assetStore.writePhoto("usr_a", "missing-local".encodeToByteArray(), "png")
        fileSystem.delete(assetStore.model(missing).toPath())
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(source = MeetupPhotoSource.LocalAlbum, localAsset = missing),
            ),
        )
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertEquals("Network Name", state.config.profile.displayName)
        assertNull(state.photoModel)
        assertTrue("photo" in assertIs<MeetupRefreshResult.Partial>(state.lastRefresh).failedParts)
    }

    @Test
    fun refreshWithoutExistingConfigDoesNotCreateOne() = repositoryTest {
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        repository.refresh("usr_a").join()

        assertFalse(repository.isConfigured("usr_a"))
        assertNull(configDao.load("usr_a"))
    }

    @Test
    fun unchangedProfileBackgroundUrlIsNotRedownloadedAndKeepsRevision() = repositoryTest {
        val backgroundAsset = assetStore.writePhoto(
            "usr_a",
            "cached-background".encodeToByteArray(),
            "webp",
        )
        val backgroundPhoto = MeetupPhoto(
            source = MeetupPhotoSource.ProfileBackground,
            sourceUrl = "https://cdn/background.webp",
            localAsset = backgroundAsset,
            width = 1200,
            height = 675,
        )
        val cached = cachedConfig("usr_a", "Cached Name").copy(
            photo = backgroundPhoto,
            profileBackgroundFallback = backgroundPhoto,
        )
        configDao.save(cached)
        remote.profileHandler = { ownerId ->
            remoteProfile(ownerId, "Cached Name").copy(
                avatarUrl = "",
                pronouns = "",
                languages = emptyList(),
                status = "",
                statusDescription = "",
                profileBackgroundUrl = "https://cdn/background.webp",
            )
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        repository.refresh("usr_a").join()

        val state = repository.observe("usr_a").value
        assertTrue(remote.imageRequests.isEmpty())
        assertEquals(MeetupRefreshResult.Success, state.lastRefresh)
        assertEquals(backgroundPhoto, state.config.photo)
        assertEquals(assetStore.model(backgroundAsset), state.photoModel)
        assertEquals(cached.revision, state.config.revision)
        assertEquals(1200, state.config.photo.width)
    }

    @Test
    fun galleryPhotoReplacedDuringRefreshIsNotOverwrittenByProfileBackground() = repositoryTest {
        val oldRef = assetStore.writePhoto("usr_a", "old-background".encodeToByteArray(), "webp")
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.ProfileBackground,
                    sourceUrl = "https://cdn/old.webp",
                    localAsset = oldRef,
                ),
            ),
        )
        val imageStarted = CompletableDeferred<Unit>()
        val releaseImage = CompletableDeferred<Unit>()
        remote.profileHandler = { ownerId ->
            remoteProfile(ownerId, "Network Name").copy(
                profileBackgroundUrl = "https://cdn/new.png",
            )
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        remote.imageHandler = {
            imageStarted.complete(Unit)
            releaseImage.await()
            "network-background".encodeToByteArray()
        }

        val refresh = repository.refresh("usr_a")
        withContext(Dispatchers.Default) {
            withTimeout(5_000L) { imageStarted.await() }
        }
        val galleryBytes = "gallery-photo".encodeToByteArray()
        val replaced = repository.replacePhoto(
            "usr_a",
            photoCandidate(
                source = MeetupPhotoSource.VrchatGallery,
                sourceId = "prnt_gallery",
                sourceUrl = "https://cdn/gallery.png",
                fileName = "../../gallery.png",
                bytes = galleryBytes,
            ),
        )
        assertTrue(replaced.isSuccess)
        releaseImage.complete(Unit)
        refresh.join()

        val config = repository.observe("usr_a").value.config
        assertEquals(MeetupPhotoSource.VrchatGallery, config.photo.source)
        assertEquals("prnt_gallery", config.photo.sourceId)
        assertEquals("https://cdn/new.png", config.profileBackgroundFallback?.sourceUrl)
        assertTrue(config.photo.localAsset?.relativePath?.contains("..") == false)
        assertEquals(MeetupCrop(centerOffsetX = 0.1f, zoom = 1.2f), config.portraitCrop)
        assertEquals(MeetupCrop(centerOffsetY = -0.1f, zoom = 1.4f), config.landscapeCrop)
        assertContentEquals(galleryBytes, assetStore.read(assertNotNull(config.photo.localAsset)))
    }

    @Test
    fun failedRefreshDoesNotRestoreOldPhotoModelAfterReplacement() = repositoryTest {
        val oldRef = assetStore.writePhoto("usr_a", "old-photo".encodeToByteArray(), "jpg")
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = MeetupPhoto(
                    source = MeetupPhotoSource.LocalAlbum,
                    localAsset = oldRef,
                ),
            ),
        )
        val profileStarted = CompletableDeferred<Unit>()
        val releaseProfile = CompletableDeferred<Unit>()
        remote.profileHandler = {
            profileStarted.complete(Unit)
            releaseProfile.await()
            throw IOException("profile failed")
        }
        remote.appearanceHandler = { throw IOException("appearance failed") }

        val refresh = repository.refresh("usr_a")
        profileStarted.await()
        val replacementBytes = "replacement".encodeToByteArray()
        assertTrue(
            repository.replacePhoto(
                "usr_a",
                photoCandidate(
                    source = MeetupPhotoSource.LocalAlbum,
                    sourceId = "album-new",
                    sourceUrl = null,
                    fileName = "replacement.jpg",
                    bytes = replacementBytes,
                ),
            ).isSuccess,
        )
        val replacementAsset = assertNotNull(configDao.load("usr_a")?.photo?.localAsset)
        releaseProfile.complete(Unit)
        refresh.join()

        val state = repository.observe("usr_a").value
        assertEquals("album-new", state.config.photo.sourceId)
        assertEquals(assetStore.model(replacementAsset), state.photoModel)
        assertContentEquals(replacementBytes, assetStore.read(replacementAsset))
        assertIs<MeetupRefreshResult.Failed>(state.lastRefresh)
    }

    @Test
    fun shortTextEditDuringRefreshStillAllowsProfileBackgroundUpdate() = repositoryTest {
        val oldPhoto = MeetupPhoto(
            source = MeetupPhotoSource.ProfileBackground,
            sourceUrl = "https://cdn/old.webp",
            localAsset = assetStore.writePhoto(
                "usr_a",
                "old-background".encodeToByteArray(),
                "webp",
            ),
        )
        configDao.save(cachedConfig("usr_a", "Cached Name").copy(photo = oldPhoto))
        val imageStarted = CompletableDeferred<Unit>()
        val releaseImage = CompletableDeferred<Unit>()
        remote.profileHandler = { ownerId ->
            remoteProfile(ownerId, "Network Name").copy(
                profileBackgroundUrl = "https://cdn/new.jpg",
            )
        }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        remote.imageHandler = {
            imageStarted.complete(Unit)
            releaseImage.await()
            "new-background".encodeToByteArray()
        }

        val refresh = repository.refresh("usr_a")
        withContext(Dispatchers.Default) {
            withTimeout(5_000L) { imageStarted.await() }
        }
        repository.update("usr_a") { it.copy(shortText = "local short", showShortText = true) }
        releaseImage.complete(Unit)
        refresh.join()

        val config = repository.observe("usr_a").value.config
        assertEquals("local short", config.shortText)
        assertEquals("https://cdn/new.jpg", config.photo.sourceUrl)
        assertEquals(config.profileBackgroundFallback, config.photo)
        assertNotNull(config.photo.localAsset)
    }

    @Test
    fun cancellationFromRemoteCancelsRefreshWithoutReportingFailure() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        remote.profileHandler = { throw CancellationException("cancelled") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }
        val completion = CompletableDeferred<Throwable?>()

        val refresh = repository.refresh("usr_a")
        refresh.invokeOnCompletion { cause -> completion.complete(cause) }
        refresh.join()

        assertIs<CancellationException>(completion.await())
        val state = repository.observe("usr_a").value
        assertFalse(state.refreshing)
        assertFalse(state.lastRefresh is MeetupRefreshResult.Failed)
    }

    @Test
    fun ensureDefaultIsIdempotentAndKeepsOptionalContentDisabled() = repositoryTest {
        val first = repository.ensureDefault("usr_a")
        val second = repository.ensureDefault("usr_a")

        assertEquals(first, second)
        assertEquals(MeetupCardTemplate.InfoBar, second.template)
        assertFalse(second.showAvatar)
        assertFalse(second.showPronouns)
        assertFalse(second.showLanguages)
        assertFalse(second.showStatus)
        assertFalse(second.showStatusDescription)
        assertFalse(second.showShortText)
        assertFalse(second.showQrCode)
        assertFalse(second.showIconFrame)
        assertFalse(second.showProfileEffect)
        assertFalse(second.showNameplateEffect)
    }

    @Test
    fun ensureDefaultUsesSynchronousCurrentUserDisplayName() = repositoryTest {
        val config = repository.ensureDefault("usr_a")

        assertEquals("Current Display", config.profile.displayName)
    }

    @Test
    fun updateForcesOwnerAndIncrementsLatestRevision() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name").copy(revision = 7L))

        repository.update("usr_a") { config ->
            config.copy(ownerUserId = "usr_other", revision = 99L, shortText = "edited")
        }

        val updated = assertNotNull(configDao.load("usr_a"))
        assertEquals("usr_a", updated.ownerUserId)
        assertEquals(8L, updated.revision)
        assertEquals("edited", updated.shortText)
        assertNull(configDao.load("usr_other"))
    }

    @Test
    fun ensureDefaultAloneDoesNotCountAsConfigured() = repositoryTest {
        repository.ensureDefault("usr_a")

        // 只是打开过编辑页：配置已建档，但首配未完成，长按仍应回到编辑页。
        assertNotNull(configDao.load("usr_a"))
        assertFalse(repository.isConfigured("usr_a"))

        repository.update("usr_a") { it.copy(showQrCode = true) }

        assertTrue(repository.isConfigured("usr_a"))
    }

    @Test
    fun refreshDoesNotMarkConfigured() = repositoryTest {
        configDao.save(cachedConfig("usr_a", "Cached Name"))
        remote.profileHandler = { ownerId -> remoteProfile(ownerId, "Network Name") }
        remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

        repository.refresh("usr_a").join()

        assertEquals("Network Name", repository.observe("usr_a").value.config.profile.displayName)
        assertFalse(repository.isConfigured("usr_a"))
    }

    @Test
    fun landscapeTargetReplacesOnlyLandscapePhotoAndCrop() = repositoryTest {
        val primary = assetStore.writePhoto("usr_a", "primary".encodeToByteArray(), "png")
        val primaryPhoto = MeetupPhoto(source = MeetupPhotoSource.LocalAlbum, localAsset = primary)
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                photo = primaryPhoto,
                portraitCrop = MeetupCrop(zoom = 1.5f),
                landscapeCrop = MeetupCrop(zoom = 1.6f),
            ),
        )

        val replaced = repository.replacePhoto(
            "usr_a",
            photoCandidate(
                source = MeetupPhotoSource.LocalAlbum,
                sourceId = null,
                sourceUrl = null,
                fileName = "landscape.png",
                bytes = "landscape-photo".encodeToByteArray(),
            ),
            MeetupPhotoTarget.Landscape,
        )

        assertTrue(replaced.isSuccess)
        val state = repository.observe("usr_a").value
        assertEquals(primaryPhoto, state.config.photo)
        assertEquals(MeetupCrop(zoom = 1.5f), state.config.portraitCrop)
        assertEquals(MeetupCrop(centerOffsetY = -0.1f, zoom = 1.4f), state.config.landscapeCrop)
        val landscapeAsset = assertNotNull(state.config.landscapePhoto?.localAsset)
        assertContentEquals("landscape-photo".encodeToByteArray(), assetStore.read(landscapeAsset))
        assertEquals(assetStore.model(landscapeAsset), state.landscapePhotoModel)
        assertEquals(assetStore.model(primary), state.photoModel)
    }

    @Test
    fun bothTargetClearsLandscapeOverride() = repositoryTest {
        val landscapeAsset = assetStore.writePhoto("usr_a", "old-landscape".encodeToByteArray(), "png")
        configDao.save(
            cachedConfig("usr_a", "Cached Name").copy(
                landscapePhoto = MeetupPhoto(
                    source = MeetupPhotoSource.LocalAlbum,
                    localAsset = landscapeAsset,
                ),
            ),
        )

        val replaced = repository.replacePhoto(
            "usr_a",
            photoCandidate(
                source = MeetupPhotoSource.LocalAlbum,
                sourceId = null,
                sourceUrl = null,
                fileName = "shared.png",
                bytes = "shared-photo".encodeToByteArray(),
            ),
            MeetupPhotoTarget.Both,
        )

        assertTrue(replaced.isSuccess)
        val state = repository.observe("usr_a").value
        assertNull(state.config.landscapePhoto)
        assertNull(state.landscapePhotoModel)
        assertContentEquals(
            "shared-photo".encodeToByteArray(),
            assetStore.read(assertNotNull(state.config.photo.localAsset)),
        )
    }

    @Test
    fun fatalErrorFromRemoteEscapesRefreshBoundary() = runTest {
        val uncaught = CompletableDeferred<Throwable>()
        val repositoryScope = CoroutineScope(
            coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, error ->
                uncaught.complete(error)
            },
        )
        val fixture = RepositoryFixture(repositoryScope)
        try {
            fixture.configDao.save(cachedConfig("usr_a", "Cached Name"))
            fixture.remote.profileHandler = { throw AssertionError("fatal") }
            fixture.remote.appearanceHandler = { ownerId -> MeetupRemoteAppearance(id = ownerId) }

            val refresh = fixture.repository.refresh("usr_a")
            assertIs<AssertionError>(withTimeout(1_000L) { uncaught.await() })
            refresh.join()
            assertTrue(refresh.isCancelled)
            assertFalse(fixture.repository.observe("usr_a").value.refreshing)
            assertFalse(
                fixture.repository.observe("usr_a").value.lastRefresh is MeetupRefreshResult.Failed,
            )
        } finally {
            repositoryScope.cancel()
            fixture.fileSystem.checkNoOpenFiles()
        }
    }

    private fun repositoryTest(block: suspend RepositoryFixture.() -> Unit) = runTest {
        val fixture = RepositoryFixture(this)
        try {
            fixture.block()
        } finally {
            fixture.fileSystem.checkNoOpenFiles()
        }
    }

    private suspend fun RepositoryFixture.blockAccountMutations(
        ownerId: String,
    ): AccountMutationBlocker {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val token = accountCacheManager.captureWriteToken(ownerId)
        val job = scope.launch {
            accountCacheManager.mutateIfCurrent(token) {
                started.complete(Unit)
                release.await()
            }
        }
        started.await()
        return AccountMutationBlocker(release, job)
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000L) {
                while (!condition()) yield()
            }
        }
    }

    private fun RepositoryFixture.photoFiles(ownerId: String) =
        "/meetup-assets/accounts/$ownerId/photos".toPath().let { directory ->
            if (fileSystem.exists(directory)) fileSystem.list(directory) else emptyList()
        }

    private fun cachedConfig(ownerId: String, displayName: String): MeetupCardConfig =
        defaultMeetupCardConfig(ownerId).copy(
            profile = MeetupProfileSnapshot(displayName = displayName),
        )

    private fun remoteProfile(ownerId: String, displayName: String) = MeetupRemoteProfile(
        id = ownerId,
        displayName = displayName,
        avatarUrl = "https://cdn/avatar.webp",
        pronouns = "they/them",
        languages = listOf("eng"),
        status = "active",
        statusDescription = "Available",
        profileBackgroundUrl = "",
    )

    private fun photoCandidate(
        source: MeetupPhotoSource,
        sourceId: String?,
        sourceUrl: String?,
        fileName: String,
        bytes: ByteArray,
    ) = MeetupPhotoCandidate(
        source = source,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        fileName = fileName,
        bytes = bytes,
        width = 1200,
        height = 1600,
        portraitCrop = MeetupCrop(centerOffsetX = 0.1f, zoom = 1.2f),
        landscapeCrop = MeetupCrop(centerOffsetY = -0.1f, zoom = 1.4f),
    )
}

private class RepositoryFixture(val scope: CoroutineScope) {
    val currentUserProfile = MeetupProfileSnapshot(displayName = "Current Display")
    val fileSystem = FakeFileSystem()
    val assetStore = MeetupCardAssetStore(fileSystem, "/meetup-assets".toPath())
    val configDao = MeetupCardConfigDao(MapSettings())
    val remote = FakeMeetupCardRemoteDataSource()
    val decorationSource = FakeRepositoryDecorationTemplateSource()
    val decorationBytesLoader = FakeRepositoryDecorationBytesLoader()
    val decorationCacheDao = DecorationTemplateCacheDao(MapSettings())
    private val decorationResolver = DecorationResolver(
        source = decorationSource,
        remoteBytesLoader = decorationBytesLoader,
        assetStore = assetStore,
        animatedWebpDecoder = object : AnimatedWebpDecoder {
            override fun decode(bytes: ByteArray): DecodedAnimation =
                error("No animation expected")
        },
        cacheDao = decorationCacheDao,
    )
    val accountCacheManager = AccountCacheManager(
        friendListCacheDao = FriendListCacheDao(MapSettings()),
        userProfileCacheDao = UserProfileCacheDao(MapSettings()),
        friendActivityStore = NoOpFriendActivityCacheStore,
        meetupCardConfigDao = configDao,
        meetupCardAssetStore = assetStore,
    )
    val repository: MeetupCardRepository by lazy {
        DefaultMeetupCardRepository(
            configDao = configDao,
            assetStore = assetStore,
            remote = remote,
            decorationResolver = decorationResolver,
            accountCacheManager = accountCacheManager,
            currentUserSnapshotProvider = MeetupCurrentUserSnapshotProvider { ownerId ->
                currentUserProfile.takeIf { ownerId == "usr_a" }
            },
            scope = scope,
        )
    }
}

private class FakeMeetupCardRemoteDataSource : MeetupCardRemoteDataSource {
    val imageRequests = mutableListOf<String>()
    val profileRequests = mutableListOf<String>()
    var profileHandler: suspend (String) -> MeetupRemoteProfile = { ownerId ->
        error("No profile configured for $ownerId")
    }
    var appearanceHandler: suspend (String) -> MeetupRemoteAppearance = { ownerId ->
        error("No appearance configured for $ownerId")
    }
    var imageHandler: suspend (String) -> ByteArray = { url ->
        error("No image configured for $url")
    }

    override suspend fun getProfile(ownerId: String): MeetupRemoteProfile {
        profileRequests += ownerId
        return profileHandler(ownerId)
    }

    override suspend fun getAppearance(ownerId: String): MeetupRemoteAppearance =
        appearanceHandler(ownerId)

    override suspend fun loadImage(url: String): ByteArray {
        imageRequests += url
        return imageHandler(url)
    }
}

private class AccountMutationBlocker(
    private val release: CompletableDeferred<Unit>,
    private val job: Job,
) {
    suspend fun release() {
        release.complete(Unit)
        job.join()
    }
}

private class FakeRepositoryDecorationTemplateSource : DecorationTemplateSource {
    val requests = mutableListOf<String>()
    var handler: suspend (String) -> InventoryTemplateData = { templateId ->
        InventoryTemplateData(
            id = templateId,
            metadata = InventoryTemplateMetadata(),
        )
    }

    override suspend fun getTemplate(templateId: String): InventoryTemplateData {
        requests += templateId
        return handler(templateId)
    }
}

private class FakeRepositoryDecorationBytesLoader : MeetupRemoteBytesLoader {
    val requests = mutableListOf<String>()
    var handler: suspend (String, Long) -> ByteArray = { url, _ ->
        error("No decoration bytes configured for $url")
    }

    override suspend fun load(url: String, maxBytes: Long): ByteArray {
        requests += url
        return handler(url, maxBytes)
    }
}

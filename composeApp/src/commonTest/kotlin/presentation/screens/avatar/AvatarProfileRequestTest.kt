package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteGroupData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntrySource
import io.github.vrcmteam.vrcm.presentation.favorites.FavoriteEntryState
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStringsEn
import io.github.vrcmteam.vrcm.service.SessionBoundResponse
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarProfileRequestTest : MainDispatcherTest() {
    private val models = mutableListOf<AvatarProfileScreenModel>()

    @AfterTest
    fun disposeModels() {
        models.forEach(::clearViewModel)
    }

    @Test
    fun avatarNoticesUseErrorOnlyForBannedAvatars() {
        assertTrue(AvatarProfileNotice.Banned.localizedToast(LocaleStringsEn) is ToastText.Error)
        assertTrue(AvatarProfileNotice.Switched.localizedToast(LocaleStringsEn) is ToastText.Success)
        assertTrue(AvatarProfileNotice.Copied.localizedToast(LocaleStringsEn) is ToastText.Success)
        val selectionFailure = AvatarProfileNotice.SelectionFailed(message = null)
            .localizedToast(LocaleStringsEn)
        assertTrue(selectionFailure is ToastText.Error)
        assertEquals(LocaleStringsEn.avatarProfileSelectFailed, selectionFailure.text)
    }

    @Test
    fun selectableAvatarActionsUseTheSameSwitchLabel() {
        assertEquals(
            LocaleStringsEn.avatarProfileActionSwitch,
            AvatarActionAvailability.Own.localizedButtonText(LocaleStringsEn),
        )
        assertEquals(
            LocaleStringsEn.avatarProfileActionSwitch,
            AvatarActionAvailability.Copyable.localizedButtonText(LocaleStringsEn),
        )
    }

    @Test
    fun directEntryLoadsFavoritedStateWithoutVisitingFavoritesFirst() = runBlocking {
        val favoriteSource = DirectEntryFavoriteSource(favoriteId = "avtr_saved")
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_saved", avatarName = "Saved"))
        yield()

        assertEquals(FavoriteEntryState.Favorited, model.favoriteEntryState.value)
    }

    @Test
    fun persistedFavoriteCacheShowsEntryStateWithoutRemoteLoading() = runBlocking {
        val favoriteSource = CachedEntryFavoriteSource(cachedMembership = true)
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_saved", avatarName = "Saved"))
        yield()

        assertEquals(FavoriteEntryState.Favorited, model.favoriteEntryState.value)
        assertEquals(0, favoriteSource.loadCount)
    }

    @Test
    fun loadedFavoriteGroupsShowEntryStateWithoutRemoteLoading() = runBlocking {
        val group = FavoriteGroupData(
            id = "group_avatars1",
            ownerId = "usr_owner",
            type = FavoriteType.Avatar.value,
            visibility = "private",
            displayName = "Avatars 1",
            name = "avatars1",
            ownerDisplayName = "Owner",
            tags = emptyList(),
        )
        val favoriteSource = LoadedGroupsFavoriteSource(
            group = group,
            favorite = FavoriteData(
                favoriteId = "avtr_saved",
                id = "fvrt_saved",
                tags = listOf(group.name),
                type = FavoriteType.Avatar.value,
            ),
        )
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_saved", avatarName = "Saved"))

        assertEquals(FavoriteEntryState.Favorited, model.favoriteEntryState.value)
        assertEquals(0, favoriteSource.loadCount)
    }

    @Test
    fun completeFavoriteCacheShowsNotFavoritedWithoutRemoteLoading() = runBlocking {
        val favoriteSource = CachedEntryFavoriteSource(cachedMembership = false)
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_missing", avatarName = "Missing"))
        yield()

        assertEquals(FavoriteEntryState.NotFavorited, model.favoriteEntryState.value)
        assertEquals(0, favoriteSource.loadCount)
    }

    @Test
    fun missingAvatarCacheFallsBackToRemoteLoading() = runBlocking {
        val favoriteSource = CachedEntryFavoriteSource(cachedMembership = null)
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_saved", avatarName = "Saved"))
        yield()

        assertEquals(1, favoriteSource.loadCount)
        assertEquals(FavoriteEntryState.NotFavorited, model.favoriteEntryState.value)
    }

    @Test
    fun cachedFavoriteStateReloadsWhenTheAccountSessionChanges() = runBlocking {
        val session = MutableStateFlow(
            AuthenticatedAccount(
                account = AccountDto(userId = "usr_account_a"),
                token = AccountSessionToken(userId = "usr_account_a", generation = 1),
            )
        )
        val favoriteSource = SessionAwareCachedEntryFavoriteSource(
            memberships = mapOf(
                "usr_account_a" to true,
                "usr_account_b" to false,
            ),
            session = session,
        )
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
            favoriteSession = session,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_saved", avatarName = "Saved"))
        yield()
        assertEquals(FavoriteEntryState.Favorited, model.favoriteEntryState.value)

        session.value = AuthenticatedAccount(
            account = AccountDto(userId = "usr_account_b"),
            token = AccountSessionToken(userId = "usr_account_b", generation = 2),
        )
        yield()

        assertEquals(FavoriteEntryState.NotFavorited, model.favoriteEntryState.value)
        assertEquals(2, favoriteSource.cachedLookupCount)
    }

    @Test
    fun failedFavoriteEntryLoadCanRetryWithoutLeavingTheProfile() = runBlocking {
        val favoriteSource = DirectEntryFavoriteSource(
            favoriteId = "avtr_saved",
            failuresBeforeSuccess = 1,
        )
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_saved", avatarName = "Saved"))
        yield()
        assertEquals(FavoriteEntryState.LoadFailed, model.favoriteEntryState.value)

        model.retryFavoriteEntryLoad()
        yield()
        assertEquals(FavoriteEntryState.Favorited, model.favoriteEntryState.value)
    }

    @Test
    fun blankAvatarIdMakesFavoriteEntryUnavailableWithoutLoading() = runBlocking {
        val favoriteSource = CountingFavoriteEntrySource()
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "", avatarName = "Unavailable"))
        yield()

        assertEquals(FavoriteEntryState.Unavailable, model.favoriteEntryState.value)
        assertEquals(0, favoriteSource.loadCount)
    }

    @Test
    fun pendingFavoriteLoadCannotReviveTheEntryAfterSwitchingToABlankAvatar() = runBlocking {
        val pendingLoad = CompletableDeferred<Unit>()
        val favoriteSource = DirectEntryFavoriteSource(
            favoriteId = "avtr_saved",
            pendingLoad = pendingLoad,
        )
        val model = avatarModel(
            loader = ControlledAvatarProfileLoader(),
            favoriteSource = favoriteSource,
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_saved", avatarName = "Saved"))
        yield()
        assertEquals(FavoriteEntryState.Loading, model.favoriteEntryState.value)

        model.refreshAvatarData(AvatarProfileVo(avatarId = "", avatarName = "Unavailable"))
        yield()
        assertEquals(FavoriteEntryState.Unavailable, model.favoriteEntryState.value)

        pendingLoad.complete(Unit)
        yield()

        assertEquals(FavoriteEntryState.Unavailable, model.favoriteEntryState.value)
    }

    @Test
    fun olderSuccessCannotOverwriteTheLatestAvatar() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = avatarModel(loader)

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_a", avatarName = "Initial A"))
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_b", avatarName = "Initial B"))
        assertTrue(model.isLoading.value)

        loader.completeSuccess("avtr_b", avatarName = "Remote B")
        assertEquals("avtr_b", model.avatarProfileState.value?.avatarId)
        assertEquals("Remote B", model.avatarProfileState.value?.avatarName)
        assertFalse(model.isLoading.value)

        loader.completeSuccess("avtr_a", avatarName = "Remote A")
        assertEquals("avtr_b", model.avatarProfileState.value?.avatarId)
        assertEquals("Remote B", model.avatarProfileState.value?.avatarName)
        assertFalse(model.isLoading.value)
    }

    @Test
    fun olderFailureDoesNotStopLoadingOrEmitToastForTheLatestRequest() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = avatarModel(loader)
        val toasts = mutableListOf<ToastText>()
        val toastCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            SharedFlowCentre.toastText.collect(toasts::add)
        }

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_a"))
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_b"))
        loader.completeFailure("avtr_a", IllegalStateException("stale failure"))
        yield()

        assertTrue(model.isLoading.value)
        assertTrue(toasts.isEmpty())

        loader.completeSuccess("avtr_b", avatarName = "Remote B")
        assertEquals("avtr_b", model.avatarProfileState.value?.avatarId)
        assertFalse(model.isLoading.value)
        toastCollector.cancel()
    }

    @Test
    fun current404RetainsCachedContentAndEmitsFriendlyNoticeWithoutRawToast() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = avatarModel(loader)
        val toasts = mutableListOf<ToastText>()
        val notices = mutableListOf<AvatarProfileNotice>()
        val toastCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            SharedFlowCentre.toastText.collect(toasts::add)
        }
        val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }

        model.refreshAvatarData(
            AvatarProfileVo(avatarId = "avtr_banned", avatarName = "Cached")
        )
        loader.completeFailure(
            "avtr_banned",
            VRCApiException("Not Found", 404, "raw body"),
        )
        yield()

        assertEquals("Cached", model.avatarProfileState.value?.avatarName)
        assertFalse(model.isLoading.value)
        assertEquals(AvatarActionAvailability.Banned, model.actionState.value.availability)
        assertEquals(listOf<AvatarProfileNotice>(AvatarProfileNotice.Banned), notices)
        assertTrue(toasts.isEmpty())
        noticeCollector.cancel()
        toastCollector.cancel()
    }

    @Test
    fun ordinaryFailureRetainsCachedContentAndEmitsAnError() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = avatarModel(loader)
        val toasts = mutableListOf<ToastText>()
        val toastCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            SharedFlowCentre.toastText.collect(toasts::add)
        }

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_1", avatarName = "Cached"))
        loader.completeFailure("avtr_1", IllegalStateException("offline"))
        yield()

        assertEquals("Cached", model.avatarProfileState.value?.avatarName)
        assertFalse(model.isLoading.value)
        assertEquals(AvatarActionAvailability.CheckFailed, model.actionState.value.availability)
        assertEquals("offline", toasts.single().text)
        toastCollector.cancel()
    }

    @Test
    fun stale404CannotReplaceTheLatestAvatar() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = avatarModel(loader)
        val notices = mutableListOf<AvatarProfileNotice>()
        val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_old"))
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_new"))
        loader.completeSuccess("avtr_new", avatarName = "Remote New")
        loader.completeFailure(
            "avtr_old",
            VRCApiException("Not Found", 404, "raw body"),
        )

        assertEquals("avtr_new", model.avatarProfileState.value?.avatarId)
        assertEquals("Remote New", model.avatarProfileState.value?.avatarName)
        assertTrue(notices.isEmpty())
        noticeCollector.cancel()
    }

    @Test
    fun currentAvatarRemainsCurrentWhenValidationReturns404() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = avatarModel(
            loader = loader,
            selector = FakeAvatarSelector(currentAvatarId = "avtr_current"),
        )

        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_current"))
        loader.completeFailure(
            "avtr_current",
            VRCApiException("Not Found", 404, "raw body"),
        )
        yield()

        assertEquals(AvatarActionAvailability.Current, model.actionState.value.availability)
    }

    @Test
    fun validatedAvatarDerivesOwnershipAndCopyabilityActions() = runBlocking {
        val cases = listOf(
            Triple("usr_current", "private", AvatarActionAvailability.Own),
            Triple("usr_other", "public", AvatarActionAvailability.Copyable),
            Triple("usr_other", "private", AvatarActionAvailability.NotCopyable),
        )

        cases.forEachIndexed { index, (authorId, releaseStatus, expected) ->
            val avatarId = "avtr_$index"
            val loader = ControlledAvatarProfileLoader()
            val model = avatarModel(loader)

            model.refreshAvatarData(AvatarProfileVo(avatarId = avatarId))
            loader.completeSuccess(
                avatarId = avatarId,
                avatarName = "Remote",
                authorId = authorId,
                releaseStatus = releaseStatus,
            )
            yield()

            assertEquals(expected, model.actionState.value.availability)
        }
    }

    @Test
    fun allowedActionsSelectOnceAndBecomeCurrentAfterSuccess() = runBlocking {
        val cases = listOf(
            Triple("usr_current", "private", AvatarProfileNotice.Switched),
            Triple("usr_other", "public", AvatarProfileNotice.Copied),
        )

        cases.forEachIndexed { index, (authorId, releaseStatus, expectedNotice) ->
            val avatarId = "avtr_select_$index"
            val loader = ControlledAvatarProfileLoader()
            val selector = FakeAvatarSelector()
            val model = avatarModel(loader, selector)
            val notices = mutableListOf<AvatarProfileNotice>()
            val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                model.notices.collect(notices::add)
            }
            model.refreshAvatarData(AvatarProfileVo(avatarId = avatarId))
            loader.completeSuccess(
                avatarId = avatarId,
                avatarName = "Remote",
                authorId = authorId,
                releaseStatus = releaseStatus,
            )
            yield()

            model.selectAvatar()
            model.selectAvatar()
            yield()

            assertTrue(model.actionState.value.isSelecting)
            assertEquals(listOf(avatarId), selector.selectedAvatarIds)

            selector.completeSelection(Result.success(Unit))
            yield()

            assertFalse(model.actionState.value.isSelecting)
            assertEquals(AvatarActionAvailability.Current, model.actionState.value.availability)
            assertEquals(listOf(expectedNotice), notices)
            noticeCollector.cancel()
        }
    }

    @Test
    fun selectionFailureRestoresActionAndEmitsErrorNotice() = runBlocking {
        val avatarId = "avtr_owned"
        val loader = ControlledAvatarProfileLoader()
        val selector = FakeAvatarSelector()
        val model = avatarModel(loader, selector)
        val notices = mutableListOf<AvatarProfileNotice>()
        val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.refreshAvatarData(AvatarProfileVo(avatarId = avatarId))
        loader.completeSuccess(
            avatarId = avatarId,
            avatarName = "Owned",
            authorId = "usr_current",
            releaseStatus = "private",
        )
        yield()

        model.selectAvatar()
        selector.completeSelection(Result.failure(IllegalStateException("select failed")))
        yield()

        assertFalse(model.actionState.value.isSelecting)
        assertEquals(AvatarActionAvailability.Own, model.actionState.value.availability)
        assertEquals(
            listOf<AvatarProfileNotice>(AvatarProfileNotice.SelectionFailed("select failed")),
            notices,
        )
        noticeCollector.cancel()
    }

    @Test
    fun selection404DisablesTheActionAsBanned() = runBlocking {
        val avatarId = "avtr_removed"
        val loader = ControlledAvatarProfileLoader()
        val selector = FakeAvatarSelector()
        val model = avatarModel(loader, selector)
        val notices = mutableListOf<AvatarProfileNotice>()
        val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.refreshAvatarData(AvatarProfileVo(avatarId = avatarId))
        loader.completeSuccess(
            avatarId = avatarId,
            avatarName = "Owned",
            authorId = "usr_current",
            releaseStatus = "private",
        )
        yield()

        model.selectAvatar()
        selector.completeSelection(
            Result.failure(VRCApiException("Not Found", 404, "raw body"))
        )
        yield()

        assertFalse(model.actionState.value.isSelecting)
        assertEquals(AvatarActionAvailability.Banned, model.actionState.value.availability)
        assertEquals(listOf<AvatarProfileNotice>(AvatarProfileNotice.Banned), notices)
        noticeCollector.cancel()
    }

    @Test
    fun accountSwitchPreventsAnOldMetadataResultFromUpdatingThePage() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val selector = FakeAvatarSelector()
        val editor = FakeAvatarEditor()
        val model = avatarModel(loader, selector, editor = editor)
        val notices = mutableListOf<AvatarProfileNotice>()
        val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_owned"))
        loader.completeSuccess(
            avatarId = "avtr_owned",
            avatarName = "Before",
            authorId = "usr_current",
        )
        yield()

        model.saveMetadata("After", "Description")
        yield()
        selector.switchAccount("usr_other")
        yield()
        editor.completeMetadata(
            Result.success(
                AvatarData(
                    id = "avtr_owned",
                    name = "After",
                    description = "Description",
                    authorId = "usr_current",
                )
            )
        )
        yield()

        assertEquals("Before", model.avatarProfileState.value?.avatarName)
        assertTrue(notices.isEmpty())
        noticeCollector.cancel()
    }

    @Test
    fun publicationControlsRequireValidatedOwnedSupportedStatus() = runBlocking {
        data class Case(
            val authorId: String,
            val releaseStatus: String,
            val sessionUserId: String,
            val expected: AvatarPublicationStatus?,
        )

        val cases = listOf(
            Case("usr_current", "private", "usr_current", AvatarPublicationStatus.Private),
            Case("usr_current", "public", "usr_current", AvatarPublicationStatus.Public),
            Case("usr_current", "hidden", "usr_current", null),
            Case("usr_other", "public", "usr_current", null),
            Case("usr_current", "private", "usr_other", null),
        )

        cases.forEachIndexed { index, case ->
            val avatarId = "avtr_publication_$index"
            val loader = ControlledAvatarProfileLoader()
            val session = MutableStateFlow<AuthenticatedAccount?>(
                authenticatedSession(case.sessionUserId, generation = index.toLong() + 1)
            )
            val model = avatarModel(loader = loader, favoriteSession = session)

            model.refreshAvatarData(
                AvatarProfileVo(
                    avatarId = avatarId,
                    authorId = case.authorId,
                    releaseStatus = case.releaseStatus,
                )
            )
            yield()
            assertEquals(null, model.editState.value.publication)

            loader.completeSuccess(
                avatarId = avatarId,
                avatarName = "Remote",
                authorId = case.authorId,
                releaseStatus = case.releaseStatus,
            )
            yield()

            assertEquals(case.expected, model.editState.value.publication)
        }
    }

    @Test
    fun unvalidatedReplacementCannotUseThePreviousPublicationState() = runBlocking {
        val fixture = publicationFixture()

        fixture.model.refreshAvatarData(
            AvatarProfileVo(
                avatarId = "avtr_unvalidated",
                avatarName = "Cached",
                authorId = "usr_current",
                releaseStatus = "private",
            )
        )
        fixture.model.updatePublication(AvatarPublicationStatus.Public)
        yield()

        assertTrue(fixture.editor.publicationRequests.isEmpty())
        assertEquals(null, fixture.model.editState.value.publication)
    }

    @Test
    fun publicationUpdateIsSingleNonOptimisticAndUsesAuthoritativeResponse() = runBlocking {
        val fixture = publicationFixture()
        val notices = mutableListOf<AvatarProfileNotice>()
        val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            fixture.model.notices.collect(notices::add)
        }

        fixture.model.updatePublication(AvatarPublicationStatus.Public)
        fixture.model.updatePublication(AvatarPublicationStatus.Public)
        yield()

        assertEquals(1, fixture.editor.publicationRequests.size)
        assertEquals("public", fixture.editor.publicationRequests.single().releaseStatus)
        assertTrue(fixture.model.editState.value.isUpdatingPublication)
        assertEquals("private", fixture.model.avatarProfileState.value?.releaseStatus)

        fixture.editor.completePublication(
            result = Result.success(
                publicationAvatar(
                    name = "Authoritative",
                    releaseStatus = "public",
                    imageUrl = "https://example.test/authoritative.png",
                    version = 7,
                )
            )
        )
        yield()

        assertFalse(fixture.model.editState.value.isUpdatingPublication)
        assertEquals("Authoritative", fixture.model.avatarProfileState.value?.avatarName)
        assertEquals("public", fixture.model.avatarProfileState.value?.releaseStatus)
        assertEquals(
            "https://example.test/authoritative.png",
            fixture.model.avatarProfileState.value?.avatarImageUrl,
        )
        assertEquals(7, fixture.model.avatarProfileState.value?.version)
        assertEquals(
            listOf<AvatarProfileNotice>(AvatarProfileNotice.PublicationMadePublic),
            notices,
        )
        noticeCollector.cancel()
    }

    @Test
    fun avatarRemoteWritesAreMutuallyExclusive() = runBlocking {
        val publicationFirst = publicationFixture()
        publicationFirst.model.updatePublication(AvatarPublicationStatus.Public)
        publicationFirst.model.saveMetadata("Renamed", "Description")
        yield()

        assertEquals(1, publicationFirst.editor.publicationRequests.size)
        assertTrue(publicationFirst.editor.metadataRequests.isEmpty())
        publicationFirst.editor.completePublication(Result.success(publicationAvatar()))
        yield()

        val metadataFirst = publicationFixture(avatarId = "avtr_metadata_first")
        metadataFirst.model.saveMetadata("Renamed", "Description")
        metadataFirst.model.updatePublication(AvatarPublicationStatus.Public)
        yield()

        assertEquals(1, metadataFirst.editor.metadataRequests.size)
        assertTrue(metadataFirst.editor.publicationRequests.isEmpty())
        metadataFirst.editor.completeMetadata(
            Result.success(
                publicationAvatar(
                    id = "avtr_metadata_first",
                    name = "Renamed",
                    releaseStatus = "private",
                )
            )
        )
        yield()
        assertFalse(metadataFirst.model.editState.value.isSavingMetadata)
    }

    @Test
    fun malformedCurrentPublicationResponsesKeepStateAndEmitFailure() = runBlocking {
        val responses = listOf(
            publicationAvatar(id = "avtr_other"),
            publicationAvatar(authorId = "usr_other"),
            publicationAvatar(releaseStatus = "hidden"),
            publicationAvatar(releaseStatus = "private"),
        )

        responses.forEach { response ->
            val fixture = publicationFixture()
            val notices = mutableListOf<AvatarProfileNotice>()
            val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.model.notices.collect(notices::add)
            }

            fixture.model.updatePublication(AvatarPublicationStatus.Public)
            yield()
            fixture.editor.completePublication(Result.success(response))
            yield()

            assertEquals("Owned", fixture.model.avatarProfileState.value?.avatarName)
            assertEquals("private", fixture.model.avatarProfileState.value?.releaseStatus)
            assertFalse(fixture.model.editState.value.isUpdatingPublication)
            assertEquals(
                listOf<AvatarProfileNotice>(
                    AvatarProfileNotice.PublicationUpdateFailed(
                        AvatarPublicationFailure.Other
                    )
                ),
                notices,
            )
            noticeCollector.cancel()
        }
    }

    @Test
    fun refreshedSessionTokenCanOwnThePublicationResponse() = runBlocking {
        val fixture = publicationFixture(generation = 1)

        fixture.model.updatePublication(AvatarPublicationStatus.Public)
        yield()
        val refreshed = authenticatedSession("usr_current", generation = 2)
        fixture.session.value = refreshed
        yield()
        fixture.editor.completePublication(
            result = Result.success(publicationAvatar()),
            responseToken = refreshed.token,
        )
        yield()

        assertEquals(1, fixture.editor.publicationRequests.single().sessionToken.generation)
        assertEquals("public", fixture.model.avatarProfileState.value?.releaseStatus)
        assertEquals(AvatarPublicationStatus.Public, fixture.model.editState.value.publication)
    }

    @Test
    fun unrelatedSameAccountSessionDiscardsTheOldPublicationResponse() = runBlocking {
        val fixture = publicationFixture(generation = 1)
        val originalToken = fixture.session.value!!.token

        fixture.model.updatePublication(AvatarPublicationStatus.Public)
        yield()
        fixture.session.value = authenticatedSession("usr_current", generation = 2)
        yield()
        fixture.editor.completePublication(
            result = Result.success(publicationAvatar()),
            responseToken = originalToken,
        )
        yield()

        assertEquals("private", fixture.model.avatarProfileState.value?.releaseStatus)
        assertFalse(fixture.model.editState.value.isUpdatingPublication)
    }

    @Test
    fun sessionAndTargetChangesDiscardLatePublicationResponses() = runBlocking {
        listOf("account", "logout", "avatar").forEach { change ->
            val fixture = publicationFixture()
            val originalToken = fixture.session.value!!.token
            val notices = mutableListOf<AvatarProfileNotice>()
            val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.model.notices.collect(notices::add)
            }

            fixture.model.updatePublication(AvatarPublicationStatus.Public)
            yield()
            when (change) {
                "account" -> {
                    fixture.selector.switchAccount("usr_other")
                    fixture.session.value = authenticatedSession("usr_other", generation = 2)
                }
                "logout" -> fixture.session.value = null
                "avatar" -> fixture.model.refreshAvatarData(
                    AvatarProfileVo(
                        avatarId = "avtr_new",
                        avatarName = "New",
                        authorId = "usr_current",
                        releaseStatus = "private",
                    )
                )
            }
            yield()
            fixture.editor.completePublication(
                result = Result.success(publicationAvatar()),
                responseToken = originalToken,
            )
            yield()

            if (change == "avatar") {
                assertEquals("avtr_new", fixture.model.avatarProfileState.value?.avatarId)
            } else {
                assertEquals("private", fixture.model.avatarProfileState.value?.releaseStatus)
            }
            assertFalse(fixture.model.editState.value.isUpdatingPublication)
            assertTrue(notices.isEmpty())
            noticeCollector.cancel()
        }
    }

    @Test
    fun publicationFailuresKeepTheAuthoritativeStateAndUseSpecificFeedback() = runBlocking {
        data class Case(
            val status: Int?,
            val failure: AvatarPublicationFailure,
            val message: String,
        )

        val cases = listOf(
            Case(
                400,
                AvatarPublicationFailure.BadRequest,
                LocaleStringsEn.avatarEditPublicationBadRequest,
            ),
            Case(
                401,
                AvatarPublicationFailure.Unauthorized,
                LocaleStringsEn.avatarEditPublicationUnauthorized,
            ),
            Case(
                403,
                AvatarPublicationFailure.Forbidden,
                LocaleStringsEn.avatarEditPublicationForbidden,
            ),
            Case(
                404,
                AvatarPublicationFailure.NotFound,
                LocaleStringsEn.avatarEditPublicationNotFound,
            ),
            Case(
                null,
                AvatarPublicationFailure.Other,
                LocaleStringsEn.avatarEditPublicationFailed,
            ),
        )

        cases.forEach { case ->
            val fixture = publicationFixture()
            val notices = mutableListOf<AvatarProfileNotice>()
            val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.model.notices.collect(notices::add)
            }

            fixture.model.updatePublication(AvatarPublicationStatus.Public)
            yield()
            val error = case.status?.let {
                VRCApiException("Request failed", it, "untrusted response")
            } ?: IllegalStateException("offline")
            fixture.editor.completePublication(Result.failure(error))
            yield()

            assertEquals("private", fixture.model.avatarProfileState.value?.releaseStatus)
            assertEquals(
                listOf<AvatarProfileNotice>(
                    AvatarProfileNotice.PublicationUpdateFailed(case.failure)
                ),
                notices,
            )
            val toast = AvatarProfileNotice.PublicationUpdateFailed(case.failure)
                .localizedToast(LocaleStringsEn)
            assertTrue(toast is ToastText.Error)
            assertEquals(case.message, toast.text)
            noticeCollector.cancel()
        }
    }

    @Test
    fun completedEditorCoverUpdatesTheCurrentAvatarAndEmitsSuccess() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val model = avatarModel(loader)
        val notices = mutableListOf<AvatarProfileNotice>()
        val noticeCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            model.notices.collect(notices::add)
        }
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_owned"))
        loader.completeSuccess(
            avatarId = "avtr_owned",
            avatarName = "Owned",
            authorId = "usr_current",
        )
        yield()

        val applied = model.applyCoverUpdate(
            AvatarData(
                id = "avtr_owned",
                name = "Owned",
                authorId = "usr_current",
                imageUrl = "https://example.test/cover.png",
                thumbnailImageUrl = "https://example.test/thumbnail.png",
                version = 2,
            )
        )
        yield()

        assertTrue(applied)
        assertEquals(
            "https://example.test/cover.png",
            model.avatarProfileState.value?.avatarImageUrl,
        )
        assertEquals(2, model.avatarProfileState.value?.version)
        assertEquals(listOf<AvatarProfileNotice>(AvatarProfileNotice.CoverSaved), notices)
        noticeCollector.cancel()
    }

    private fun avatarModel(
        loader: AvatarProfileLoader,
        selector: AvatarSelector = FakeAvatarSelector(),
        favoriteSource: FavoriteEntrySource = EmptyFavoriteEntrySource(),
        editor: AvatarEditor? = null,
        favoriteSession: StateFlow<AuthenticatedAccount?> = SharedFlowCentre.currentSession,
    ): AvatarProfileScreenModel =
        AvatarProfileScreenModel(
            loader,
            selector,
            favoriteSource,
            Dispatchers.Unconfined,
            editor,
            EmptyAvatarImpostorDeletionSource,
            favoriteSession,
        )
            .also(models::add)

    private suspend fun publicationFixture(
        avatarId: String = "avtr_owned",
        generation: Long = 1,
    ): PublicationFixture {
        val loader = ControlledAvatarProfileLoader()
        val selector = FakeAvatarSelector()
        val editor = FakeAvatarEditor()
        val session = MutableStateFlow<AuthenticatedAccount?>(
            authenticatedSession("usr_current", generation)
        )
        val model = avatarModel(
            loader = loader,
            selector = selector,
            editor = editor,
            favoriteSession = session,
        )
        model.refreshAvatarData(
            AvatarProfileVo(
                avatarId = avatarId,
                avatarName = "Cached",
                authorId = "usr_current",
                releaseStatus = "private",
            )
        )
        loader.completeSuccess(
            avatarId = avatarId,
            avatarName = "Owned",
            authorId = "usr_current",
            releaseStatus = "private",
        )
        yield()
        return PublicationFixture(model, selector, editor, session)
    }
}

private data class PublicationFixture(
    val model: AvatarProfileScreenModel,
    val selector: FakeAvatarSelector,
    val editor: FakeAvatarEditor,
    val session: MutableStateFlow<AuthenticatedAccount?>,
)

private fun authenticatedSession(userId: String, generation: Long) = AuthenticatedAccount(
    account = AccountDto(userId = userId),
    token = AccountSessionToken(userId = userId, generation = generation),
)

private fun publicationAvatar(
    id: String = "avtr_owned",
    name: String = "Owned",
    authorId: String = "usr_current",
    releaseStatus: String = "public",
    imageUrl: String = "",
    version: Int? = null,
) = AvatarData(
    id = id,
    name = name,
    description = "Description",
    authorId = authorId,
    authorName = "Author",
    imageUrl = imageUrl,
    releaseStatus = releaseStatus,
    tags = listOf("system_approved"),
    version = version,
)

private class EmptyFavoriteEntrySource : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favorites

    override suspend fun load(type: FavoriteType): Result<Unit> = Result.success(Unit)
}

private class CountingFavoriteEntrySource : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())
    var loadCount = 0
        private set

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favorites

    override suspend fun load(type: FavoriteType): Result<Unit> {
        loadCount++
        return Result.success(Unit)
    }
}

private class CachedEntryFavoriteSource(
    private val cachedMembership: Boolean?,
) : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())
    var loadCount = 0
        private set

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favorites

    override suspend fun cachedFavorite(type: FavoriteType, favoriteId: String): Boolean? = cachedMembership

    override suspend fun load(type: FavoriteType): Result<Unit> {
        loadCount++
        return Result.success(Unit)
    }
}

private class SessionAwareCachedEntryFavoriteSource(
    private val memberships: Map<String, Boolean>,
    private val session: StateFlow<AuthenticatedAccount?>,
) : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())
    var cachedLookupCount = 0
        private set

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favorites

    override suspend fun cachedFavorite(type: FavoriteType, favoriteId: String): Boolean? {
        cachedLookupCount++
        return session.value?.token?.userId?.let(memberships::get)
    }

    override suspend fun load(type: FavoriteType): Result<Unit> = Result.success(Unit)
}

private class LoadedGroupsFavoriteSource(
    group: FavoriteGroupData,
    favorite: FavoriteData,
) : FavoriteEntrySource {
    private val favorites = MutableStateFlow(mapOf(group to listOf(favorite)))
    var loadCount = 0
        private set

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favorites

    override suspend fun load(type: FavoriteType): Result<Unit> {
        loadCount++
        return Result.success(Unit)
    }
}

private class DirectEntryFavoriteSource(
    private val favoriteId: String,
    private val failuresBeforeSuccess: Int = 0,
    private val pendingLoad: CompletableDeferred<Unit>? = null,
) : FavoriteEntrySource {
    private val favorites = MutableStateFlow<Map<FavoriteGroupData, List<FavoriteData>>>(emptyMap())
    private var attempts = 0

    override fun favoritesByGroup(type: FavoriteType): StateFlow<Map<FavoriteGroupData, List<FavoriteData>>> =
        favorites

    override suspend fun load(type: FavoriteType): Result<Unit> {
        pendingLoad?.await()
        if (attempts++ < failuresBeforeSuccess) {
            return Result.failure(IllegalStateException("favorite groups unavailable"))
        }
        val group = FavoriteGroupData(
            id = "group_avatars1",
            ownerId = "usr_owner",
            type = type.value,
            visibility = "private",
            displayName = "Avatars 1",
            name = "avatars1",
            ownerDisplayName = "Owner",
            tags = emptyList(),
        )
        favorites.value = mapOf(
            group to listOf(
                FavoriteData(
                    favoriteId = favoriteId,
                    id = "fvrt_saved",
                    tags = listOf(group.name),
                    type = type.value,
                )
            )
        )
        return Result.success(Unit)
    }
}

private fun clearViewModel(viewModel: ViewModel) {
    ViewModelStore().apply {
        put("test", viewModel)
        clear()
    }
}

private class ControlledAvatarProfileLoader : AvatarProfileLoader {
    private val requests = mutableMapOf<String, CompletableDeferred<Result<AvatarData>>>()

    override suspend fun load(avatarId: String): Result<AvatarData> =
        requests.getOrPut(avatarId) { CompletableDeferred() }.await()

    fun completeSuccess(
        avatarId: String,
        avatarName: String,
        authorId: String = "",
        releaseStatus: String = "",
    ) {
        requests.getValue(avatarId).complete(
            Result.success(
                AvatarData(
                    id = avatarId,
                    name = avatarName,
                    authorId = authorId,
                    releaseStatus = releaseStatus,
                )
            )
        )
    }

    fun completeFailure(avatarId: String, error: Throwable) {
        requests.getValue(avatarId).complete(Result.failure(error))
    }
}

private class FakeAvatarSelector(
    userId: String = "usr_current",
    currentAvatarId: String = "avtr_current",
) : AvatarSelector {
    private val mutableCurrentUser = MutableStateFlow(
        AvatarUserContext(userId = userId, currentAvatarId = currentAvatarId)
    )
    override val currentUser: StateFlow<AvatarUserContext?> = mutableCurrentUser
    val selectedAvatarIds = mutableListOf<String>()
    private var selection = CompletableDeferred<Result<Unit>>()

    override suspend fun select(avatarId: String): Result<Unit> {
        selectedAvatarIds += avatarId
        return selection.await().onSuccess {
            mutableCurrentUser.value = mutableCurrentUser.value.copy(currentAvatarId = avatarId)
        }
    }

    fun completeSelection(result: Result<Unit>) {
        selection.complete(result)
    }

    fun switchAccount(userId: String) {
        mutableCurrentUser.value = AvatarUserContext(userId, "avtr_other")
    }
}

private class FakeAvatarEditor : AvatarEditor {
    private val metadata = CompletableDeferred<Result<AvatarData>>()
    private val publication = CompletableDeferred<AvatarPublicationResponse?>()
    val metadataRequests = mutableListOf<Pair<String, AvatarUpdateData>>()
    val publicationRequests = mutableListOf<PublicationRequest>()

    override suspend fun updateMetadata(
        avatarId: String,
        update: AvatarUpdateData,
    ): Result<AvatarData> {
        metadataRequests += avatarId to update
        return metadata.await()
    }

    override suspend fun updatePublication(
        sessionToken: AccountSessionToken,
        avatarId: String,
        releaseStatus: String,
    ): AvatarPublicationResponse? {
        publicationRequests += PublicationRequest(sessionToken, avatarId, releaseStatus)
        return publication.await()
    }

    override suspend fun uploadCover(cover: AvatarCoverFile): Result<String> =
        Result.failure(IllegalStateException("Cover upload is not used"))

    override suspend fun assignCover(avatarId: String, imageUrl: String): Result<AvatarData> =
        Result.failure(IllegalStateException("Cover assignment is not used"))

    fun completeMetadata(result: Result<AvatarData>) {
        metadata.complete(result)
    }

    fun completePublication(
        result: Result<AvatarData>,
        responseToken: AccountSessionToken = publicationRequests.single().sessionToken,
    ) {
        publication.complete(AvatarPublicationResponse(result, responseToken))
    }
}

private data object EmptyAvatarImpostorDeletionSource : AvatarImpostorDeletionSource {
    override suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? = null

    override suspend fun load(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<AvatarData>? = null
}
private data class PublicationRequest(
    val sessionToken: AccountSessionToken,
    val avatarId: String,
    val releaseStatus: String,
)

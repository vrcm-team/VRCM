package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AuthenticatedAccount
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarStyle
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
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_current", 1))
        val model = avatarModel(
            loader,
            selector,
            editor = editor,
            favoriteSession = session,
        )
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

        model.saveMetadata(
            AvatarMetadataDraft(
                name = "After",
                description = "Description",
                contentTags = emptySet(),
                authorTags = "",
            )
        )
        yield()
        session.value = authenticated("usr_other", 2)
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
    fun metadataSaveUsesServerResponseAsTheAuthoritativeState() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val editor = FakeAvatarEditor()
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_current", 1))
        val model = avatarModel(loader, editor = editor, favoriteSession = session)
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_owned"))
        loader.completeSuccess(
            avatarId = "avtr_owned",
            avatarName = "Before",
            authorId = "usr_current",
        )
        yield()

        model.saveMetadata(
            AvatarMetadataDraft(
                name = "Requested",
                description = "Requested description",
                contentTags = setOf("content_horror"),
                authorTags = "dance",
            )
        )
        yield()
        editor.completeMetadata(
            Result.success(
                AvatarData(
                    id = "avtr_owned",
                    name = "Server normalized",
                    description = "Server description",
                    authorId = "usr_current",
                    tags = listOf("content_horror", "author_tag_dance"),
                )
            )
        )
        yield()

        assertEquals("Server normalized", model.avatarProfileState.value?.avatarName)
        assertEquals("Server description", model.avatarProfileState.value?.avatarDescription)
        assertEquals(
            listOf("content_horror", "author_tag_dance"),
            model.avatarProfileState.value?.tags,
        )
    }

    @Test
    fun repeatedMetadataSaveOnlyStartsOneRequest() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val editor = FakeAvatarEditor()
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_current", 1))
        val model = avatarModel(loader, editor = editor, favoriteSession = session)
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_owned"))
        loader.completeSuccess("avtr_owned", "Before", authorId = "usr_current")
        yield()
        val draft = AvatarMetadataDraft(
            name = "After",
            description = "Description",
            contentTags = emptySet(),
            authorTags = "",
        )

        model.saveMetadata(draft)
        model.saveMetadata(draft)
        yield()

        assertEquals(1, editor.metadataRequests)
        assertTrue(model.editState.value.isSavingMetadata)
    }

    @Test
    fun sameAccountRenewalKeepsMetadataSaveLockedAndAcceptsRenewedResponse() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val editor = FakeAvatarEditor()
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_current", 1))
        val model = avatarModel(loader, editor = editor, favoriteSession = session)
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_owned"))
        loader.completeSuccess("avtr_owned", "Before", authorId = "usr_current")
        yield()

        model.saveMetadata(
            AvatarMetadataDraft(
                name = "After",
                description = "Description",
                contentTags = emptySet(),
                authorTags = "",
            )
        )
        yield()

        session.value = authenticated("usr_current", 2)
        yield()
        assertTrue(model.editState.value.isSavingMetadata)

        editor.metadataResponseToken = AccountSessionToken("usr_current", 2)
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

        assertEquals("After", model.avatarProfileState.value?.avatarName)
        assertFalse(model.editState.value.isSavingMetadata)
    }

    @Test
    fun accountSwitchDiscardsAnInFlightStyleList() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val editor = FakeAvatarEditor()
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_current", 1))
        val model = avatarModel(loader, editor = editor, favoriteSession = session)
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_owned"))
        loader.completeSuccess("avtr_owned", "Owned", authorId = "usr_current")
        yield()

        model.loadAvatarStyles()
        yield()
        assertEquals(AvatarStylesLoadState.Loading, model.editState.value.styles)

        session.value = authenticated("usr_other", 2)
        yield()
        editor.completeStyles(
            Result.success(listOf(AvatarStyle("avst_old", "Old session style")))
        )
        yield()

        assertEquals(AvatarStylesLoadState.NotLoaded, model.editState.value.styles)
        assertFalse(model.editState.value.canEdit)
    }

    @Test
    fun sameAccountRenewalKeepsInFlightStyleListUsable() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val editor = FakeAvatarEditor()
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_current", 1))
        val model = avatarModel(loader, editor = editor, favoriteSession = session)
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_owned"))
        loader.completeSuccess("avtr_owned", "Owned", authorId = "usr_current")
        yield()

        model.loadAvatarStyles()
        yield()
        assertEquals(AvatarStylesLoadState.Loading, model.editState.value.styles)

        session.value = authenticated("usr_current", 2)
        yield()
        editor.stylesResponseToken = AccountSessionToken("usr_current", 2)
        editor.completeStyles(
            Result.success(listOf(AvatarStyle("avst_new", "Renewed style")))
        )
        yield()

        assertEquals(
            AvatarStylesLoadState.Ready(listOf(AvatarStyle("avst_new", "Renewed style"))),
            model.editState.value.styles,
        )
    }

    @Test
    fun sameAccountRenewalNullStyleResponseBecomesRetryableFailure() = runBlocking {
        val loader = ControlledAvatarProfileLoader()
        val editor = FakeAvatarEditor()
        val session = MutableStateFlow<AuthenticatedAccount?>(authenticated("usr_current", 1))
        val model = avatarModel(loader, editor = editor, favoriteSession = session)
        model.refreshAvatarData(AvatarProfileVo(avatarId = "avtr_owned"))
        loader.completeSuccess("avtr_owned", "Owned", authorId = "usr_current")
        yield()

        model.loadAvatarStyles()
        yield()
        assertEquals(AvatarStylesLoadState.Loading, model.editState.value.styles)

        session.value = authenticated("usr_current", 2)
        editor.returnNullStyles = true
        editor.completeStyles(Result.success(emptyList()))
        yield()

        assertEquals(AvatarStylesLoadState.Failed(null), model.editState.value.styles)
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
            favoriteSession,
        )
            .also(models::add)
}

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
    private val styles = CompletableDeferred<Result<List<AvatarStyle>>>()
    var metadataRequests = 0
        private set
    var metadataResponseToken: AccountSessionToken? = null
    var stylesResponseToken: AccountSessionToken? = null
    var returnNullStyles: Boolean = false

    override suspend fun loadStyles(
        sessionToken: AccountSessionToken,
    ): AvatarStylesResponse? {
        val result = styles.await()
        if (returnNullStyles) return null
        return AvatarStylesResponse(
            result,
            stylesResponseToken ?: sessionToken,
        )
    }

    override suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        avatarId: String,
        update: AvatarUpdateData,
    ): AvatarMetadataUpdateResponse {
        metadataRequests++
        return AvatarMetadataUpdateResponse(
            result = metadata.await(),
            sessionToken = metadataResponseToken ?: sessionToken,
        )
    }

    override suspend fun uploadCover(cover: AvatarCoverFile): Result<String> =
        Result.failure(IllegalStateException("Cover upload is not used"))

    override suspend fun assignCover(avatarId: String, imageUrl: String): Result<AvatarData> =
        Result.failure(IllegalStateException("Cover assignment is not used"))

    fun completeMetadata(result: Result<AvatarData>) {
        metadata.complete(result)
    }

    fun completeStyles(result: Result<List<AvatarStyle>>) {
        styles.complete(result)
    }

}

private fun authenticated(userId: String, generation: Long) = AuthenticatedAccount(
    account = AccountDto(userId = userId),
    token = AccountSessionToken(userId = userId, generation = generation),
)

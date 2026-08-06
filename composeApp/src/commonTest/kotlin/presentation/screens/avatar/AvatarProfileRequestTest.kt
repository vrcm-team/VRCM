package io.github.vrcmteam.vrcm.presentation.screens.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.avatar.data.AvatarProfileVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LocaleStringsEn
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
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
        val model = avatarModel(loader, selector, editor)
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
        editor: AvatarEditor? = null,
    ): AvatarProfileScreenModel =
        AvatarProfileScreenModel(loader, selector, Dispatchers.Unconfined, editor)
            .also(models::add)
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

    override suspend fun updateMetadata(
        avatarId: String,
        update: AvatarUpdateData,
    ): Result<AvatarData> = metadata.await()

    override suspend fun uploadCover(cover: AvatarCoverFile): Result<String> =
        Result.failure(IllegalStateException("Cover upload is not used"))

    override suspend fun assignCover(avatarId: String, imageUrl: String): Result<AvatarData> =
        Result.failure(IllegalStateException("Cover assignment is not used"))

    fun completeMetadata(result: Result<AvatarData>) {
        metadata.complete(result)
    }

}

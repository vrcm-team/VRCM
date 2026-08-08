package io.github.vrcmteam.vrcm.presentation.screens.meetup

import androidx.compose.ui.graphics.ImageBitmap
import io.github.vrcmteam.vrcm.presentation.screens.meetup.editor.MeetupPhotoSessionStore
import io.github.vrcmteam.vrcm.presentation.screens.meetup.editor.MeetupPreparedPhoto
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardRepository
import io.github.vrcmteam.vrcm.service.meetup.MeetupCardState
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoCandidate
import io.github.vrcmteam.vrcm.service.meetup.MeetupPhotoTarget
import io.github.vrcmteam.vrcm.service.meetup.MeetupRefreshResult
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardConfig
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCrop
import io.github.vrcmteam.vrcm.storage.meetup.MeetupOrientation
import io.github.vrcmteam.vrcm.storage.meetup.MeetupPhotoSource
import io.github.vrcmteam.vrcm.storage.meetup.MeetupProfileSnapshot
import io.github.vrcmteam.vrcm.storage.meetup.defaultMeetupCardConfig
import io.github.vrcmteam.vrcm.testing.MainDispatcherTest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class MeetupCardScreenModelTest : MainDispatcherTest() {

    @Test
    fun cachedStateIsReadyWithoutWaitingForRefresh() = runTest {
        val repository = FakeMeetupCardRepository(cachedConfig("usr_a", "Cached Name"))
        repository.refreshCompletes = false
        val model = model(repository)

        assertEquals("Cached Name", model.state.value.displayName)
        assertFalse(model.state.value.blockingLoading)
        advanceUntilIdle()
        assertEquals("Cached Name", model.state.value.displayName)
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun shortTextRejectsMoreThanEightyCodePointsWithoutPersisting() = runTest {
        val repository = FakeMeetupCardRepository(cachedConfig("usr_a", "Cached Name"))
        val model = model(repository)
        advanceUntilIdle()

        model.setShortText("😀".repeat(81))
        advanceUntilIdle()

        assertIs<MeetupEditorError.ShortTextTooLong>(model.state.value.editorError)
        assertEquals("", repository.state.value.config.shortText)

        model.setShortText("😀".repeat(80))
        advanceUntilIdle()

        assertEquals("😀".repeat(80), repository.state.value.config.shortText)
    }

    @Test
    fun duplicatePhotoConfirmationCommitsOnce() = runTest {
        val repository = FakeMeetupCardRepository(cachedConfig("usr_a", "Cached Name"))
        val sessions = MeetupPhotoSessionStore(releasePreview = {})
        val session = sessions.create(preparedPhoto())
        val model = model(repository, sessions)
        advanceUntilIdle()

        model.confirmPhoto(session.id)
        model.confirmPhoto(session.id)
        advanceUntilIdle()

        assertEquals(1, repository.photoReplacements)
        assertNull(sessions.get(session.id))
    }

    @Test
    fun failedPhotoConfirmationKeepsSessionForRetry() = runTest {
        val repository = FakeMeetupCardRepository(cachedConfig("usr_a", "Cached Name"))
        repository.replacePhotoFails = true
        val sessions = MeetupPhotoSessionStore(releasePreview = {})
        val session = sessions.create(preparedPhoto())
        val model = model(repository, sessions)
        advanceUntilIdle()

        model.confirmPhoto(session.id)
        advanceUntilIdle()

        assertIs<MeetupEditorError.PhotoFailed>(model.state.value.editorError)
        assertEquals(session, sessions.get(session.id))

        repository.replacePhotoFails = false
        model.clearError()
        model.confirmPhoto(session.id)
        advanceUntilIdle()

        assertEquals(1, repository.photoReplacements)
        assertNull(sessions.get(session.id))
    }

    @Test
    fun cropDraftOnlyPersistsOnCommitAndOrientationSwitchDropsIt() = runTest {
        val repository = FakeMeetupCardRepository(cachedConfig("usr_a", "Cached Name"))
        val model = model(repository)
        advanceUntilIdle()

        model.updateCropDraft(MeetupCrop(.2f, .1f, 2f))
        advanceUntilIdle()
        assertEquals(MeetupCrop(), repository.state.value.config.portraitCrop)
        assertEquals(MeetupCrop(.2f, .1f, 2f), model.state.value.activeCrop)

        model.setOrientation(MeetupOrientation.Landscape)
        assertNull(model.state.value.cropDraft)

        model.updateCropDraft(MeetupCrop(-.1f, 0f, 3f))
        model.commitCrop()
        advanceUntilIdle()
        assertEquals(MeetupCrop(-.1f, 0f, 3f), repository.state.value.config.landscapeCrop)
        assertEquals(MeetupCrop(), repository.state.value.config.portraitCrop)
    }

    private fun model(
        repository: MeetupCardRepository,
        sessions: MeetupPhotoSessionStore = MeetupPhotoSessionStore(releasePreview = {}),
    ) = MeetupCardScreenModel(
        ownerUserId = "usr_a",
        repository = repository,
        photoSessions = sessions,
    )

    private fun cachedConfig(ownerId: String, displayName: String): MeetupCardConfig =
        defaultMeetupCardConfig(ownerId).copy(
            profile = MeetupProfileSnapshot(displayName = displayName),
        )

    private fun preparedPhoto(): MeetupPreparedPhoto = MeetupPreparedPhoto(
        candidate = MeetupPhotoCandidate(
            source = MeetupPhotoSource.LocalAlbum,
            sourceId = null,
            sourceUrl = null,
            fileName = "photo.png",
            bytes = byteArrayOf(1),
            width = 3000,
            height = 4000,
            portraitCrop = MeetupCrop(zoom = 1.5f),
            landscapeCrop = MeetupCrop(zoom = 2.5f),
        ),
        preview = ImageBitmap(4, 4),
    )
}

private class FakeMeetupCardRepository(initial: MeetupCardConfig) : MeetupCardRepository {
    val state = MutableStateFlow(
        MeetupCardState(config = initial, photoModel = null),
    )
    var refreshCalls = 0
    var refreshCompletes = true
    var photoReplacements = 0
    var replacePhotoFails = false

    override fun hasConfig(ownerId: String): Boolean = true

    override fun observe(ownerId: String): StateFlow<MeetupCardState> = state.asStateFlow()

    override suspend fun ensureDefault(ownerId: String): MeetupCardConfig = state.value.config

    override fun refresh(ownerId: String): Job {
        refreshCalls++
        return Job().also { if (refreshCompletes) it.complete() }
    }

    override suspend fun update(
        ownerId: String,
        transform: (MeetupCardConfig) -> MeetupCardConfig,
    ) {
        state.update { it.copy(config = transform(it.config)) }
    }

    override suspend fun replacePhoto(
        ownerId: String,
        candidate: MeetupPhotoCandidate,
        target: MeetupPhotoTarget,
    ): Result<Unit> {
        if (replacePhotoFails) return Result.failure(IllegalStateException("disk full"))
        photoReplacements++
        lastPhotoTarget = target
        return Result.success(Unit)
    }

    var lastPhotoTarget: MeetupPhotoTarget? = null
}

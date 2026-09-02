package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelection
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelectionSessionStore
import io.github.vrcmteam.vrcm.service.ImageInviteRemote
import io.github.vrcmteam.vrcm.service.ImageInviteRemoteResult
import io.github.vrcmteam.vrcm.service.PreparedImageInvite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImageInviteCoordinatorTest {
    @Test
    fun preparationFailureCanBeRetriedAndSentOnlyOnce() = runTest {
        val session = AccountSessionToken("usr_me", 1)
        val remote = FakeImageInviteRemote(session)
        val gallery = GallerySelectionSessionStore()
        val coordinator = ImageInviteCoordinator(
            gallerySessions = gallery,
            remote = remote,
            currentSessionToken = { session },
        )
        val selection = gallerySelection()
        val selectionSession = assertNotNull(coordinator.beginSelection("usr_friend"))
        gallery.complete(selectionSession, selection)

        remote.prepareResult = ImageInviteRemoteResult.Failure(IllegalStateException("network"), session)
        coordinator.finishSelection(selectionSession)
        assertEquals(ImageInviteFailureStage.Preparation, (coordinator.state.value as ImageInviteUiState.Failed).stage)

        remote.prepareResult = ImageInviteRemoteResult.Success(PreparedImageInvite(selection, PNG), session)
        coordinator.retryPreparation()
        assertEquals(ImageInviteUiState.Ready(selection), coordinator.state.value)

        remote.sendResult = ImageInviteRemoteResult.Success(Unit, session)
        coordinator.send()
        coordinator.send()
        assertEquals(1, remote.sendCount)
        assertEquals(ImageInviteUiState.Sent(selection), coordinator.state.value)
    }

    @Test
    fun sessionChangeDropsLatePreparationAndCancelsSelection() = runTest {
        var currentSession: AccountSessionToken? = AccountSessionToken("usr_me", 1)
        val remote = FakeImageInviteRemote(currentSession!!)
        val gallery = GallerySelectionSessionStore()
        val coordinator = ImageInviteCoordinator(gallery, remote) { currentSession }
        val selectionSession = assertNotNull(coordinator.beginSelection("usr_friend"))
        gallery.complete(selectionSession, gallerySelection())
        remote.prepareStarted = CompletableDeferred()
        remote.prepareRelease = CompletableDeferred()

        val pending = async { coordinator.finishSelection(selectionSession) }
        remote.prepareStarted!!.await()
        currentSession = AccountSessionToken("usr_other", 2)
        remote.prepareRelease!!.complete(Unit)
        pending.await()

        assertEquals(ImageInviteUiState.SessionChanged, coordinator.state.value)
        assertEquals(false, gallery.isPending(selectionSession))
    }

    @Test
    fun sendingFailureRetriesPreparedImageWithoutDownloadingAgain() = runTest {
        val session = AccountSessionToken("usr_me", 1)
        val remote = FakeImageInviteRemote(session)
        val gallery = GallerySelectionSessionStore()
        val coordinator = ImageInviteCoordinator(gallery, remote) { session }
        val selectionSession = assertNotNull(coordinator.beginSelection("usr_friend"))
        gallery.complete(selectionSession, gallerySelection())
        coordinator.finishSelection(selectionSession)

        remote.sendResult = ImageInviteRemoteResult.Failure(IllegalStateException("send"), session)
        coordinator.send()
        assertEquals(ImageInviteFailureStage.Sending, (coordinator.state.value as ImageInviteUiState.Failed).stage)

        remote.sendResult = ImageInviteRemoteResult.Success(Unit, session)
        coordinator.send()
        assertEquals(1, remote.prepareCount)
        assertEquals(2, remote.sendCount)
        assertEquals(ImageInviteUiState.Sent(gallerySelection()), coordinator.state.value)
    }

    private class FakeImageInviteRemote(
        private val session: AccountSessionToken,
    ) : ImageInviteRemote {
        var prepareResult: ImageInviteRemoteResult<PreparedImageInvite> =
            ImageInviteRemoteResult.Success(PreparedImageInvite(gallerySelection(), PNG), session)
        var sendResult: ImageInviteRemoteResult<Unit> = ImageInviteRemoteResult.Success(Unit, session)
        var prepareStarted: CompletableDeferred<Unit>? = null
        var prepareRelease: CompletableDeferred<Unit>? = null
        var prepareCount = 0
        var sendCount = 0

        override suspend fun prepare(
            selection: GallerySelection,
            sessionToken: AccountSessionToken,
        ): ImageInviteRemoteResult<PreparedImageInvite> {
            prepareCount++
            prepareStarted?.complete(Unit)
            prepareRelease?.await()
            return prepareResult
        }

        override suspend fun send(
            userId: String,
            image: PreparedImageInvite,
            sessionToken: AccountSessionToken,
        ): ImageInviteRemoteResult<Unit> {
            sendCount++
            return sendResult
        }
    }

    private companion object {
        val PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )

        fun gallerySelection() = GallerySelection(
            fileId = "file_gallery",
            fileName = "invite",
            extension = ".png",
            imageUrl = "https://api.vrchat.cloud/api/1/image/file_gallery/1/2048",
        )
    }
}

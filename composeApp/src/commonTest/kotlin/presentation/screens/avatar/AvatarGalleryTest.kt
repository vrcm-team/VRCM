package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class AvatarGalleryTest {
    @Test
    fun successfulGetWithoutUploadedFileRemainsRetryable() {
        val token = AccountSessionToken("usr_owner", 1)
        val pending = pendingRefresh(token, uploadedFile = file("file_uploaded"))

        val result = authoritativeAvatarGalleryUpdate(
            pending = pending,
            files = listOf(file("file_older")),
            sessionToken = token,
        )

        val failure = assertIs<AvatarGalleryUploadFailure.Refresh>(result.exceptionOrNull())
        assertSame(pending, failure.pending)
    }

    @Test
    fun refreshSucceedsOnlyAfterUploadedFileIsVisible() {
        val token = AccountSessionToken("usr_owner", 1)
        val uploaded = file("file_uploaded")
        val pending = pendingRefresh(token, uploaded)
        val files = listOf(file("file_older"), uploaded)

        val update = authoritativeAvatarGalleryUpdate(pending, files, token).getOrThrow()

        assertEquals("avtr_owner", update.avatarId)
        assertEquals(files, update.files)
        assertEquals(token, update.sessionToken)
    }

    private fun pendingRefresh(
        token: AccountSessionToken,
        uploadedFile: FileData,
    ) = AvatarGalleryPendingRefresh(
        target = AvatarGalleryTarget("avtr_owner", token.userId, token),
        uploadedFile = uploadedFile,
        sessionToken = token,
    )

    private fun file(id: String) = FileData(
        id = id,
        name = "$id.png",
        ownerId = "usr_owner",
        mimeType = "image/png",
        extension = ".png",
        animationStyle = null,
        tags = listOf("avatargallery"),
        versions = emptyList(),
    )
}

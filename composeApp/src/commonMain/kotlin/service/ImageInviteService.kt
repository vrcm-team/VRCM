package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.presentation.screens.gallery.GallerySelection
import io.github.vrcmteam.vrcm.service.meetup.MeetupRemoteBytesLoader

internal data class PreparedImageInvite(
    val selection: GallerySelection,
    val bytes: ByteArray,
)

internal sealed interface ImageInviteRemoteResult<out T> {
    data class Success<T>(
        val value: T,
        val sessionToken: AccountSessionToken,
    ) : ImageInviteRemoteResult<T>

    data class Failure(
        val error: Throwable,
        val sessionToken: AccountSessionToken,
    ) : ImageInviteRemoteResult<Nothing>

    data object SessionChanged : ImageInviteRemoteResult<Nothing>
}

internal interface ImageInviteRemote {
    suspend fun prepare(
        selection: GallerySelection,
        sessionToken: AccountSessionToken,
    ): ImageInviteRemoteResult<PreparedImageInvite>

    suspend fun send(
        userId: String,
        image: PreparedImageInvite,
        sessionToken: AccountSessionToken,
    ): ImageInviteRemoteResult<Unit>
}

/** Downloads the selected Gallery image and submits it through the authenticated photo invite API. */
internal class ImageInviteService(
    private val authService: AuthService,
    private val inviteApi: InviteApi,
    private val bytesLoader: MeetupRemoteBytesLoader,
) : ImageInviteRemote {
    override suspend fun prepare(
        selection: GallerySelection,
        sessionToken: AccountSessionToken,
    ): ImageInviteRemoteResult<PreparedImageInvite> {
        val response = authService.runSessionBoundCatching(sessionToken) {
            require(selection.fileId.startsWith("file_") && selection.imageUrl.isNotBlank()) {
                "Gallery selection is invalid"
            }
            val sourceUrl = FileApi.convertFileUrlToOriginal(selection.imageUrl)
                .ifBlank { selection.imageUrl }
            val bytes = bytesLoader.load(sourceUrl, MAX_INVITE_IMAGE_BYTES.toLong())
            PreparedImageInvite(selection = selection, bytes = bytes)
        } ?: return ImageInviteRemoteResult.SessionChanged
        return response.result.fold(
            onSuccess = { ImageInviteRemoteResult.Success(it, response.sessionToken) },
            onFailure = { ImageInviteRemoteResult.Failure(it, response.sessionToken) },
        )
    }

    override suspend fun send(
        userId: String,
        image: PreparedImageInvite,
        sessionToken: AccountSessionToken,
    ): ImageInviteRemoteResult<Unit> {
        val response = authService.runSessionBoundCatching(sessionToken) {
            val instanceLocation = authService.currentUser().presence.instance
            if (instanceLocation.isBlank() || instanceLocation == "offline") {
                throw ImageInviteNotInInstanceException()
            }
            inviteApi.inviteUserWithPhoto(
                userId = userId,
                instanceId = instanceLocation,
                imageBytes = image.bytes,
            )
        } ?: return ImageInviteRemoteResult.SessionChanged
        return response.result.fold(
            onSuccess = { ImageInviteRemoteResult.Success(Unit, response.sessionToken) },
            onFailure = { ImageInviteRemoteResult.Failure(it, response.sessionToken) },
        )
    }

    private companion object {
        const val MAX_INVITE_IMAGE_BYTES = 10_000_000
    }
}

internal class ImageInviteNotInInstanceException : IllegalStateException(
    "The current user is not in an instance",
)

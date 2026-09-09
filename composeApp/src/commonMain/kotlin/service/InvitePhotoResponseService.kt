package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.service.meetup.MeetupRemoteBytesLoader

internal sealed interface InvitePhotoSessionResult<out T> {
    data class Completed<T>(
        val result: Result<T>,
        val sessionToken: AccountSessionToken,
    ) : InvitePhotoSessionResult<T>

    data object SessionChanged : InvitePhotoSessionResult<Nothing>
}

/**
 * Loads a selected VRChat Gallery image and submits it through the authenticated invite-photo
 * response endpoint. The two steps are exposed separately so UI state can distinguish preparation
 * from the multipart response while carrying a renewed account session between them.
 */
class InvitePhotoResponseService(
    private val authService: AuthService,
    private val inviteApi: InviteApi,
    private val remoteBytesLoader: MeetupRemoteBytesLoader,
) {
    internal suspend fun loadGalleryPhoto(
        sessionToken: AccountSessionToken,
        fileId: String,
        imageUrl: String,
    ): InvitePhotoSessionResult<ByteArray> {
        val response = authService.runSessionBoundCatching(sessionToken) {
            require(FileApi.findFileId(imageUrl) == fileId) {
                "Gallery selection does not match its image URL"
            }
            val originalUrl = FileApi.convertFileUrlToOriginal(imageUrl)
            validateInvitePhotoPayload(
                remoteBytesLoader.load(originalUrl, MAX_INVITE_PHOTO_BYTES),
            )
        } ?: return InvitePhotoSessionResult.SessionChanged
        return InvitePhotoSessionResult.Completed(response.result, response.sessionToken)
    }

    internal suspend fun respond(
        sessionToken: AccountSessionToken,
        notificationId: String,
        imageBytes: ByteArray,
        responseSlot: Int = DEFAULT_RESPONSE_SLOT,
    ): InvitePhotoSessionResult<String> {
        val response = authService.runSessionBoundCatching(sessionToken) {
            inviteApi.respondInviteWithPhoto(notificationId, responseSlot, imageBytes)
        } ?: return InvitePhotoSessionResult.SessionChanged
        return InvitePhotoSessionResult.Completed(response.result, response.sessionToken)
    }

    companion object {
        /** Keep the downloaded source within the same bound used by Gallery image uploads. */
        const val MAX_INVITE_PHOTO_BYTES: Long = 10_000_000L
        const val DEFAULT_RESPONSE_SLOT: Int = 0
    }
}

internal fun validateInvitePhotoPayload(bytes: ByteArray): ByteArray {
    require(bytes.isNotEmpty() && bytes.size.toLong() <= InvitePhotoResponseService.MAX_INVITE_PHOTO_BYTES) {
        "Gallery image is empty or exceeds the invite photo limit"
    }
    require(bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }) {
        "Gallery image is not a PNG file"
    }
    return bytes
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)

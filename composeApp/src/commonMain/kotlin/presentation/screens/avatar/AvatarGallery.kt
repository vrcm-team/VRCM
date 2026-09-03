package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileData
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException

internal object AvatarGalleryLimits {
    const val MAX_FILE_BYTES: Long = 50L * 1024L * 1024L
    val ALLOWED_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "heic", "heif")
}

data class AvatarGalleryTarget(
    val avatarId: String,
    val ownerUserId: String,
    val sessionToken: AccountSessionToken,
) {
    init {
        require(avatarId.isNotBlank()) { "Avatar Gallery target requires an avatar ID" }
        require(ownerUserId.isNotBlank()) { "Avatar Gallery target requires an owner ID" }
        require(ownerUserId == sessionToken.userId) {
            "Avatar Gallery target must belong to the active account"
        }
    }
}

data class AvatarGalleryUpdate(
    val avatarId: String,
    val files: List<FileData>,
    val sessionToken: AccountSessionToken,
)

internal data class AvatarGalleryPendingRefresh(
    val target: AvatarGalleryTarget,
    val uploadedFile: FileData,
    val sessionToken: AccountSessionToken,
)

internal sealed class AvatarGalleryUploadFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Upload(cause: Throwable) :
        AvatarGalleryUploadFailure("Avatar Gallery upload failed", cause)

    class Refresh(
        val pending: AvatarGalleryPendingRefresh,
        cause: Throwable,
    ) : AvatarGalleryUploadFailure("Avatar Gallery refresh failed", cause)

    class SessionChanged :
        AvatarGalleryUploadFailure("Account session changed during Avatar Gallery upload")

    class Permission :
        AvatarGalleryUploadFailure("Avatar Gallery upload is not allowed for this avatar")
}

internal interface AvatarGalleryUploader {
    suspend fun uploadAndRefresh(
        target: AvatarGalleryTarget,
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onUploadProgress: suspend (bytesSent: Long, totalBytes: Long?) -> Unit,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate>

    suspend fun refresh(
        pending: AvatarGalleryPendingRefresh,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate>
}

internal class NetworkAvatarGalleryUploader(
    private val fileApi: FileApi,
    private val authService: AuthService,
) : AvatarGalleryUploader {
    override suspend fun uploadAndRefresh(
        target: AvatarGalleryTarget,
        imageBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onUploadProgress: suspend (bytesSent: Long, totalBytes: Long?) -> Unit,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate> {
        if (!isCurrentOwner(target)) return Result.failure(AvatarGalleryUploadFailure.Permission())

        val uploaded = sessionBound(target.sessionToken) {
            fileApi.uploadAvatarGalleryImage(
                fileBytes = imageBytes,
                fileName = fileName,
                mimeType = mimeType,
                avatarId = target.avatarId,
                onProgress = onUploadProgress,
            ).getOrThrow()
        }.getOrElse { cause ->
            cause.rethrowIfCancellation()
            return Result.failure(
                if (cause is AvatarGalleryUploadFailure.SessionChanged) cause
                else AvatarGalleryUploadFailure.Upload(cause),
            )
        }

        if (uploaded.value.ownerId != target.ownerUserId) {
            return Result.failure(AvatarGalleryUploadFailure.Permission())
        }

        val pending = AvatarGalleryPendingRefresh(
            target = target,
            uploadedFile = uploaded.value,
            sessionToken = uploaded.sessionToken,
        )
        return refresh(pending, onRefreshing)
    }

    override suspend fun refresh(
        pending: AvatarGalleryPendingRefresh,
        onRefreshing: () -> Unit,
    ): Result<AvatarGalleryUpdate> {
        if (!isCurrentOwner(pending.target.copy(sessionToken = pending.sessionToken))) {
            return Result.failure(AvatarGalleryUploadFailure.SessionChanged())
        }
        onRefreshing()
        val refreshed = sessionBound(pending.sessionToken) {
            fileApi.getAvatarGalleryFiles(pending.target.avatarId).getOrThrow()
        }
        return refreshed.fold(
            onSuccess = { response ->
                authoritativeAvatarGalleryUpdate(
                    pending = pending,
                    files = response.value,
                    sessionToken = response.sessionToken,
                )
            },
            onFailure = { cause ->
                cause.rethrowIfCancellation()
                Result.failure(
                    if (cause is AvatarGalleryUploadFailure.SessionChanged) cause
                    else AvatarGalleryUploadFailure.Refresh(pending, cause),
                )
            },
        )
    }

    private fun isCurrentOwner(target: AvatarGalleryTarget): Boolean =
        target.ownerUserId == target.sessionToken.userId &&
            io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre.isCurrentSession(
                target.sessionToken,
            )

    private suspend fun <T> sessionBound(
        token: AccountSessionToken,
        block: suspend () -> T,
    ): Result<SessionBoundValue<T>> {
        val response = authService.runSessionBoundCatching(token, block)
            ?: return Result.failure(AvatarGalleryUploadFailure.SessionChanged())
        return response.result.map { SessionBoundValue(it, response.sessionToken) }
    }

}

internal fun authoritativeAvatarGalleryUpdate(
    pending: AvatarGalleryPendingRefresh,
    files: List<FileData>,
    sessionToken: AccountSessionToken,
): Result<AvatarGalleryUpdate> {
    if (files.none { it.id == pending.uploadedFile.id }) {
        return Result.failure(
            AvatarGalleryUploadFailure.Refresh(
                pending,
                IllegalStateException(
                    "Uploaded Avatar Gallery file ${pending.uploadedFile.id} is not visible yet",
                ),
            )
        )
    }
    return Result.success(
        AvatarGalleryUpdate(
            avatarId = pending.target.avatarId,
            files = files,
            sessionToken = sessionToken,
        )
    )
}

private data class SessionBoundValue<T>(
    val value: T,
    val sessionToken: AccountSessionToken,
)

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

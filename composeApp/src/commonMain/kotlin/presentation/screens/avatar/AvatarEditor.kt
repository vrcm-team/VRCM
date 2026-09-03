package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarStyle
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarUpdateData
import io.github.vrcmteam.vrcm.network.api.files.FileApi
import io.github.vrcmteam.vrcm.network.api.files.data.FileTagType
import io.github.vrcmteam.vrcm.service.AuthService
import kotlinx.coroutines.CancellationException

internal sealed class AvatarCoverUpdateFailure(
    message: String,
    cause: Throwable,
) : Exception(message, cause) {
    class Upload(cause: Throwable) :
        AvatarCoverUpdateFailure("Avatar cover upload failed", cause)

    class Assignment(cause: Throwable) :
        AvatarCoverUpdateFailure("Avatar cover assignment failed", cause)
}

internal data class AvatarMetadataUpdateResponse(
    val result: Result<AvatarData>,
    val sessionToken: AccountSessionToken,
)

internal data class AvatarStylesResponse(
    val result: Result<List<AvatarStyle>>,
    val sessionToken: AccountSessionToken,
)

internal interface AvatarEditor {
    suspend fun loadStyles(sessionToken: AccountSessionToken): AvatarStylesResponse?
    suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        avatarId: String,
        update: AvatarUpdateData,
    ): AvatarMetadataUpdateResponse?
    suspend fun uploadCover(cover: AvatarCoverFile): Result<String>
    suspend fun assignCover(avatarId: String, imageUrl: String): Result<AvatarData>

    suspend fun updateCover(avatarId: String, cover: AvatarCoverFile): Result<AvatarData> {
        val imageUrl = uploadCover(cover).getOrElse { cause ->
            if (cause is CancellationException) throw cause
            return Result.failure(AvatarCoverUpdateFailure.Upload(cause))
        }
        return assignCover(avatarId, imageUrl).fold(
            onSuccess = Result.Companion::success,
            onFailure = { cause ->
                if (cause is CancellationException) throw cause
                Result.failure(AvatarCoverUpdateFailure.Assignment(cause))
            },
        )
    }
}

internal class NetworkAvatarEditor(
    private val avatarsApi: AvatarsApi,
    private val fileApi: FileApi,
    private val authService: AuthService,
) : AvatarEditor {
    override suspend fun loadStyles(sessionToken: AccountSessionToken): AvatarStylesResponse? =
        authService.runSessionBoundCatching(sessionToken) {
            avatarsApi.getAvatarStyles()
        }?.let { response ->
            AvatarStylesResponse(response.result, response.sessionToken)
        }

    override suspend fun updateMetadata(
        sessionToken: AccountSessionToken,
        avatarId: String,
        update: AvatarUpdateData,
    ): AvatarMetadataUpdateResponse? = authService.runSessionBoundCatching(sessionToken) {
        avatarsApi.updateAvatar(avatarId, update)
    }?.let { response ->
        AvatarMetadataUpdateResponse(response.result, response.sessionToken)
    }

    override suspend fun uploadCover(cover: AvatarCoverFile): Result<String> =
        authService.reTryAuthCatching {
            val file = fileApi.uploadImageFile(
                fileBytes = cover.bytes,
                fileName = cover.fileName,
                mimeType = cover.mimeType,
                tagType = FileTagType.AvatarImage,
            ).getOrThrow()
            val version = file.versions.maxOfOrNull { it.version }
                ?: error("Uploaded avatar image has no file version")
            FileApi.imageUrl(file.id, version)
        }

    override suspend fun assignCover(
        avatarId: String,
        imageUrl: String,
    ): Result<AvatarData> = authService.reTryAuthCatching {
        avatarsApi.updateAvatar(avatarId, AvatarUpdateData(imageUrl = imageUrl))
    }
}

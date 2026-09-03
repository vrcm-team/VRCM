package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarModerationApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AVATAR_BLOCK_MODERATION_TYPE
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.SessionBoundResponse

internal interface AvatarModerationSource {
    suspend fun isBlocked(avatarId: String): Result<Boolean>

    suspend fun block(avatarId: String): Result<Unit>

    suspend fun unblock(avatarId: String): Result<Unit>

    suspend fun isBlocked(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Boolean>? =
        SessionBoundResponse(isBlocked(avatarId), sessionToken)

    suspend fun block(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? =
        SessionBoundResponse(block(avatarId), sessionToken)

    suspend fun unblock(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? =
        SessionBoundResponse(unblock(avatarId), sessionToken)
}

internal class NetworkAvatarModerationSource(
    private val avatarModerationApi: AvatarModerationApi,
    private val authService: AuthService,
) : AvatarModerationSource {
    override suspend fun isBlocked(avatarId: String): Result<Boolean> =
        authService.reTryAuthCatching {
            avatarModerationApi.getAvatarModerations().any { moderation ->
                moderation.avatarModerationType == AVATAR_BLOCK_MODERATION_TYPE &&
                    moderation.targetAvatarId == avatarId
            }
        }

    override suspend fun block(avatarId: String): Result<Unit> =
        authService.reTryAuthCatching { avatarModerationApi.blockAvatar(avatarId) }.map { }

    override suspend fun unblock(avatarId: String): Result<Unit> =
        authService.reTryAuthCatching { avatarModerationApi.unblockAvatar(avatarId) }

    override suspend fun isBlocked(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Boolean>? = authService
        .runSessionBoundCatching(sessionToken) {
            avatarModerationApi.getAvatarModerations().any { moderation ->
                moderation.avatarModerationType == AVATAR_BLOCK_MODERATION_TYPE &&
                    moderation.targetAvatarId == avatarId
            }
        }

    override suspend fun block(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? = authService
        .runSessionBoundCatching(sessionToken) { avatarModerationApi.blockAvatar(avatarId) }
        ?.let { response ->
            SessionBoundResponse(response.result.map { }, response.sessionToken)
        }

    override suspend fun unblock(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): SessionBoundResponse<Unit>? = authService
        .runSessionBoundCatching(sessionToken) { avatarModerationApi.unblockAvatar(avatarId) }
}

internal enum class AvatarModerationStatus {
    Unavailable,
    Loading,
    Blocked,
    NotBlocked,
    LoadFailed,
}

internal data class AvatarModerationState(
    val avatarId: String? = null,
    val status: AvatarModerationStatus = AvatarModerationStatus.Unavailable,
    val isUpdating: Boolean = false,
)

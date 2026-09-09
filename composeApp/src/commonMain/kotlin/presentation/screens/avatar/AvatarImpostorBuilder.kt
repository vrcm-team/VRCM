package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarImpostorQueueStats
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarImpostorServiceStatus
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.service.AuthService

internal data class AuthenticatedAvatarImpostorResult<T>(
    val result: Result<T>,
    val sessionToken: AccountSessionToken,
)

internal interface AvatarImpostorBuilder {
    suspend fun enqueue(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): AuthenticatedAvatarImpostorResult<AvatarImpostorServiceStatus>?

    suspend fun queueStats(
        sessionToken: AccountSessionToken,
    ): AuthenticatedAvatarImpostorResult<AvatarImpostorQueueStats>?

    fun isCurrentSession(sessionToken: AccountSessionToken): Boolean
}

internal class NetworkAvatarImpostorBuilder(
    private val avatarsApi: AvatarsApi,
    private val authService: AuthService,
) : AvatarImpostorBuilder {
    override suspend fun enqueue(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): AuthenticatedAvatarImpostorResult<AvatarImpostorServiceStatus>? =
        authService.runSessionBoundCatching(sessionToken) {
            avatarsApi.enqueueImpostor(avatarId)
        }?.let { AuthenticatedAvatarImpostorResult(it.result, it.sessionToken) }

    override suspend fun queueStats(
        sessionToken: AccountSessionToken,
    ): AuthenticatedAvatarImpostorResult<AvatarImpostorQueueStats>? =
        authService.runSessionBoundCatching(sessionToken) {
            avatarsApi.getImpostorQueueStats()
        }?.let { AuthenticatedAvatarImpostorResult(it.result, it.sessionToken) }

    override fun isCurrentSession(sessionToken: AccountSessionToken): Boolean =
        SharedFlowCentre.isCurrentSession(sessionToken)
}

internal enum class AvatarImpostorFailure {
    Authentication,
    Permission,
    NotFound,
    Conflict,
    RateLimited,
    Server,
    InvalidResponse,
    Unknown,
}

internal fun Throwable.toAvatarImpostorFailure(): AvatarImpostorFailure = when (this) {
    is VRCApiException -> when (code) {
        401 -> AvatarImpostorFailure.Authentication
        403 -> AvatarImpostorFailure.Permission
        404 -> AvatarImpostorFailure.NotFound
        409 -> AvatarImpostorFailure.Conflict
        429 -> AvatarImpostorFailure.RateLimited
        in 500..599 -> AvatarImpostorFailure.Server
        else -> AvatarImpostorFailure.Unknown
    }
    else -> AvatarImpostorFailure.Unknown
}

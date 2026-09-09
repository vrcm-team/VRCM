package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.network.api.playermoderation.PlayerModerationApi
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationData
import io.github.vrcmteam.vrcm.network.api.playermoderation.data.PlayerModerationType

data class PlayerModerationCleanupResponse<T>(
    val result: Result<T>,
    val sessionToken: AccountSessionToken,
)

interface PlayerModerationCleanupSource {
    suspend fun getAll(
        sessionToken: AccountSessionToken,
    ): PlayerModerationCleanupResponse<List<PlayerModerationData>>?

    suspend fun get(
        sessionToken: AccountSessionToken,
        type: PlayerModerationType,
    ): PlayerModerationCleanupResponse<List<PlayerModerationData>>?

    suspend fun remove(
        sessionToken: AccountSessionToken,
        targetUserId: String,
        type: PlayerModerationType,
    ): PlayerModerationCleanupResponse<Unit>?
}

class AuthenticatedPlayerModerationCleanupSource(
    private val api: PlayerModerationApi,
    private val authService: AuthService,
) : PlayerModerationCleanupSource {
    override suspend fun getAll(
        sessionToken: AccountSessionToken,
    ): PlayerModerationCleanupResponse<List<PlayerModerationData>>? =
        authService.runSessionBoundCatching(sessionToken) { api.get() }?.let {
            PlayerModerationCleanupResponse(it.result, it.sessionToken)
        }

    override suspend fun get(
        sessionToken: AccountSessionToken,
        type: PlayerModerationType,
    ): PlayerModerationCleanupResponse<List<PlayerModerationData>>? =
        authService.runSessionBoundCatching(sessionToken) { api.get(type) }?.let {
            PlayerModerationCleanupResponse(it.result, it.sessionToken)
        }

    override suspend fun remove(
        sessionToken: AccountSessionToken,
        targetUserId: String,
        type: PlayerModerationType,
    ): PlayerModerationCleanupResponse<Unit>? =
        authService.runSessionBoundCatching(sessionToken) { api.remove(targetUserId, type) }?.let {
            PlayerModerationCleanupResponse(it.result, it.sessionToken)
        }
}

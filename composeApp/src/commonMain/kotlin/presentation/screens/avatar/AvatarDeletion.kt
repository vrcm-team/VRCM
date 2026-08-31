package io.github.vrcmteam.vrcm.presentation.screens.avatar

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.avatars.AvatarsApi
import io.github.vrcmteam.vrcm.network.api.avatars.data.AvatarData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.service.AuthService
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal const val DELETED_AVATAR_RELEASE_STATUS = "hidden"

internal data class AuthenticatedAvatarDeletion(
    val result: Result<AvatarData>,
    val sessionToken: AccountSessionToken,
)

internal interface AvatarDeleter {
    fun isCurrentSession(token: AccountSessionToken): Boolean

    suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): AuthenticatedAvatarDeletion?
}

internal class NetworkAvatarDeleter(
    private val avatarsApi: AvatarsApi,
    private val authService: AuthService,
) : AvatarDeleter {
    override fun isCurrentSession(token: AccountSessionToken): Boolean =
        SharedFlowCentre.isCurrentSession(token)

    override suspend fun delete(
        sessionToken: AccountSessionToken,
        avatarId: String,
    ): AuthenticatedAvatarDeletion? {
        val response = authService.runSessionBoundCatching(sessionToken) {
            avatarsApi.deleteAvatar(avatarId)
        } ?: return null
        return AuthenticatedAvatarDeletion(response.result, response.sessionToken)
    }
}

internal enum class AvatarDeletionFailure {
    BadRequest,
    Unauthorized,
    Forbidden,
    NotFound,
    InvalidResponse,
    Unexpected,
}

internal data class AvatarDeletionTarget(
    val sessionToken: AccountSessionToken,
    val avatarId: String,
    val avatarName: String,
)

internal data class AvatarDeletionState(
    val canDelete: Boolean = false,
    val confirmation: AvatarDeletionTarget? = null,
    val isDeleting: Boolean = false,
    val failure: AvatarDeletionFailure? = null,
)

internal fun Throwable.toAvatarDeletionFailure(): AvatarDeletionFailure =
    when ((this as? VRCApiException)?.code) {
        HttpStatusCode.BadRequest.value -> AvatarDeletionFailure.BadRequest
        HttpStatusCode.Unauthorized.value -> AvatarDeletionFailure.Unauthorized
        HttpStatusCode.Forbidden.value -> AvatarDeletionFailure.Forbidden
        HttpStatusCode.NotFound.value -> AvatarDeletionFailure.NotFound
        else -> AvatarDeletionFailure.Unexpected
    }

internal data class AvatarDeletionResults(
    val sessionToken: AccountSessionToken? = null,
    val avatars: Map<String, AvatarData> = emptyMap(),
) {
    fun deletedAvatarIds(currentToken: AccountSessionToken?): Set<String> =
        if (currentToken == sessionToken) avatars.keys else emptySet()
}

/** Keeps authoritative delete results in memory only for the active account session. */
internal class AvatarDeletionResultStore(
    private val currentSessionToken: () -> AccountSessionToken? = {
        SharedFlowCentre.currentSession.value?.token
    },
) {
    private val _results = MutableStateFlow(AvatarDeletionResults())
    val results: StateFlow<AvatarDeletionResults> = _results.asStateFlow()

    fun record(sessionToken: AccountSessionToken, avatar: AvatarData): Boolean {
        if (currentSessionToken() != sessionToken ||
            avatar.id.isBlank() ||
            avatar.authorId != sessionToken.userId ||
            avatar.releaseStatus != DELETED_AVATAR_RELEASE_STATUS
        ) {
            return false
        }

        _results.update { current ->
            val retained = current.avatars.takeIf { current.sessionToken == sessionToken }.orEmpty()
            AvatarDeletionResults(
                sessionToken = sessionToken,
                avatars = retained + (avatar.id to avatar),
            )
        }
        return true
    }
}

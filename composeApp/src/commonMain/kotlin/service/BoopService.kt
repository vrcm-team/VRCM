package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.network.api.users.UsersApi
import io.github.vrcmteam.vrcm.network.api.users.isBoopCooldown
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

sealed interface BoopResult {
    data object Sent : BoopResult
    data object Cooldown : BoopResult
    data object InFlight : BoopResult
    data object SessionChanged : BoopResult
    data class Failed(val error: Throwable) : BoopResult
}

internal data class AuthenticatedBoopResponse(
    val response: Result<VRChatResponse>,
    val sessionToken: AccountSessionToken,
)

internal fun interface BoopRequest {
    suspend fun send(
        sessionToken: AccountSessionToken,
        userId: String,
        emojiId: String?,
    ): AuthenticatedBoopResponse
}

internal class NetworkBoopRequest(
    private val authService: AuthService,
    private val usersApi: UsersApi,
) : BoopRequest {
    override suspend fun send(
        sessionToken: AccountSessionToken,
        userId: String,
        emojiId: String?,
    ): AuthenticatedBoopResponse {
        val response = authService.runSessionBoundCatching(sessionToken) {
            usersApi.boop(userId, emojiId)
        } ?: return staleBoopResponse(sessionToken)
        return AuthenticatedBoopResponse(response.result, response.sessionToken)
    }
}

private data object BoopSessionChangedException :
    IllegalStateException("Account session changed during Boop request")

private fun staleBoopResponse(token: AccountSessionToken) = AuthenticatedBoopResponse(
    response = Result.failure(BoopSessionChangedException),
    sessionToken = token,
)

internal class BoopSubmissionGate {
    private val lock = SynchronizedObject()
    private val recipients = mutableSetOf<String>()

    fun tryStart(userId: String): Boolean = synchronized(lock) {
        recipients.add(userId)
    }

    fun finish(userId: String) = synchronized(lock) {
        recipients.remove(userId)
    }
}

class BoopService internal constructor(private val request: BoopRequest) {
    private val submissionGate = BoopSubmissionGate()

    suspend fun send(userId: String, emojiId: String? = null): BoopResult {
        val sessionToken = SharedFlowCentre.currentSession.value?.token
            ?: return BoopResult.SessionChanged
        if (!submissionGate.tryStart(userId)) return BoopResult.InFlight
        return try {
            val response = request.send(sessionToken, userId, emojiId)
            if (!SharedFlowCentre.isCurrentSession(response.sessionToken)) {
                return BoopResult.SessionChanged
            }
            response.response.fold(
                    onSuccess = { response ->
                        response.toResult().fold(
                            onSuccess = { BoopResult.Sent },
                            onFailure = { error ->
                                if (error.isBoopCooldown()) BoopResult.Cooldown else BoopResult.Failed(error)
                            },
                        )
                    },
                    onFailure = { error ->
                        when {
                            error === BoopSessionChangedException -> BoopResult.SessionChanged
                            error.isBoopCooldown() -> BoopResult.Cooldown
                            else -> BoopResult.Failed(error)
                        }
                    },
                )
        } finally {
            submissionGate.finish(userId)
        }
    }
}

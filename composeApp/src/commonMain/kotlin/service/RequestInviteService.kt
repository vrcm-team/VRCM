package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

sealed interface RequestInviteResult {
    data class Sent(val sessionToken: AccountSessionToken) : RequestInviteResult
    data object InFlight : RequestInviteResult
    data object SessionChanged : RequestInviteResult
    data class Failed(
        val error: Throwable,
        val sessionToken: AccountSessionToken,
    ) : RequestInviteResult
}

internal fun interface RequestInviteCall {
    suspend fun send(
        sessionToken: AccountSessionToken,
        userId: String,
    ): SessionBoundResponse<Unit>?
}

internal class NetworkRequestInviteCall(
    private val authService: AuthService,
    private val inviteApi: InviteApi,
) : RequestInviteCall {
    override suspend fun send(
        sessionToken: AccountSessionToken,
        userId: String,
    ): SessionBoundResponse<Unit>? = authService.runSessionBoundCatching(sessionToken) {
        inviteApi.requestInvite(userId)
    }
}

private class RequestInviteSubmissionGate {
    private val lock = SynchronizedObject()
    private val recipients = mutableSetOf<String>()

    fun tryStart(userId: String): Boolean = synchronized(lock) {
        recipients.add(userId)
    }

    fun finish(userId: String) = synchronized(lock) {
        recipients.remove(userId)
    }
}

class RequestInviteService internal constructor(
    private val request: RequestInviteCall,
) {
    private val submissionGate = RequestInviteSubmissionGate()

    suspend fun send(userId: String): RequestInviteResult {
        val sessionToken = SharedFlowCentre.currentSession.value?.token
            ?: return RequestInviteResult.SessionChanged
        if (!submissionGate.tryStart(userId)) return RequestInviteResult.InFlight

        return try {
            val response = request.send(sessionToken, userId)
                ?: return RequestInviteResult.SessionChanged
            if (!SharedFlowCentre.isCurrentSession(response.sessionToken)) {
                return RequestInviteResult.SessionChanged
            }
            response.result.fold(
                onSuccess = { RequestInviteResult.Sent(response.sessionToken) },
                onFailure = { RequestInviteResult.Failed(it, response.sessionToken) },
            )
        } finally {
            submissionGate.finish(userId)
        }
    }
}

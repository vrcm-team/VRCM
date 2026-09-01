package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.invite.InviteApi
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

enum class InviteMessageAction(val messageType: InviteMessageType) {
    Invite(InviteMessageType.Message),
    RequestInvite(InviteMessageType.Request),
}

sealed interface InviteMessageLoadResult {
    data class Loaded(
        val messages: List<InviteMessageData>,
        val sessionToken: AccountSessionToken,
    ) : InviteMessageLoadResult

    data class Failed(
        val error: Throwable,
        val sessionToken: AccountSessionToken,
    ) : InviteMessageLoadResult

    data object SessionChanged : InviteMessageLoadResult
}

sealed interface InviteMessageSendResult {
    data class Sent(val sessionToken: AccountSessionToken) : InviteMessageSendResult
    data class Failed(
        val error: Throwable,
        val sessionToken: AccountSessionToken,
    ) : InviteMessageSendResult

    data class NotInInstance(val sessionToken: AccountSessionToken) : InviteMessageSendResult
    data object InFlight : InviteMessageSendResult
    data object SessionChanged : InviteMessageSendResult
}

internal interface InviteMessageActionCall {
    suspend fun load(
        sessionToken: AccountSessionToken,
        messageType: InviteMessageType,
    ): SessionBoundResponse<List<InviteMessageData>>?

    suspend fun send(
        sessionToken: AccountSessionToken,
        targetUserId: String,
        action: InviteMessageAction,
        slot: Int,
    ): SessionBoundResponse<Unit>?
}

private class NetworkInviteMessageActionCall(
    private val authService: AuthService,
    private val inviteApi: InviteApi,
) : InviteMessageActionCall {
    override suspend fun load(
        sessionToken: AccountSessionToken,
        messageType: InviteMessageType,
    ): SessionBoundResponse<List<InviteMessageData>>? =
        authService.runSessionBoundCatching(sessionToken) {
            inviteApi.getInviteMessages(sessionToken.userId, messageType)
        }

    override suspend fun send(
        sessionToken: AccountSessionToken,
        targetUserId: String,
        action: InviteMessageAction,
        slot: Int,
    ): SessionBoundResponse<Unit>? = authService.runSessionBoundCatching(sessionToken) {
        when (action) {
            InviteMessageAction.Invite -> {
                val instance = authService.currentUser().presence.instance
                if (instance.isBlank() || instance == "offline") throw NotInInstanceException
                inviteApi.inviteUser(targetUserId, instance, slot)
            }

            InviteMessageAction.RequestInvite -> inviteApi.requestInvite(targetUserId, slot)
        }
        Unit
    }
}

private data class InviteMessageSubmissionKey(
    val accountUserId: String,
    val targetUserId: String,
    val action: InviteMessageAction,
)

private class InviteMessageSubmissionGate {
    private val lock = SynchronizedObject()
    private val active = mutableSetOf<InviteMessageSubmissionKey>()

    fun tryStart(key: InviteMessageSubmissionKey): Boolean = synchronized(lock) { active.add(key) }
    fun finish(key: InviteMessageSubmissionKey) = synchronized(lock) { active.remove(key) }
}

private data object NotInInstanceException : IllegalStateException()

class InviteMessageActionService private constructor(
    private val call: InviteMessageActionCall,
) {
    constructor(authService: AuthService, inviteApi: InviteApi) :
        this(NetworkInviteMessageActionCall(authService, inviteApi))

    internal constructor(call: InviteMessageActionCall, testMarker: Unit = Unit) : this(call)

    private val submissionGate = InviteMessageSubmissionGate()

    suspend fun load(action: InviteMessageAction): InviteMessageLoadResult {
        val sessionToken = SharedFlowCentre.currentSession.value?.token
            ?: return InviteMessageLoadResult.SessionChanged
        val response = call.load(sessionToken, action.messageType)
            ?: return InviteMessageLoadResult.SessionChanged
        if (!SharedFlowCentre.isCurrentSession(response.sessionToken)) {
            return InviteMessageLoadResult.SessionChanged
        }
        return response.result.fold(
            onSuccess = { InviteMessageLoadResult.Loaded(it, response.sessionToken) },
            onFailure = { InviteMessageLoadResult.Failed(it, response.sessionToken) },
        )
    }

    suspend fun send(
        targetUserId: String,
        action: InviteMessageAction,
        slot: Int,
    ): InviteMessageSendResult {
        val sessionToken = SharedFlowCentre.currentSession.value?.token
            ?: return InviteMessageSendResult.SessionChanged
        val key = InviteMessageSubmissionKey(sessionToken.userId, targetUserId, action)
        if (!submissionGate.tryStart(key)) return InviteMessageSendResult.InFlight

        return try {
            val response = call.send(sessionToken, targetUserId, action, slot)
                ?: return InviteMessageSendResult.SessionChanged
            if (!SharedFlowCentre.isCurrentSession(response.sessionToken)) {
                return InviteMessageSendResult.SessionChanged
            }
            response.result.fold(
                onSuccess = { InviteMessageSendResult.Sent(response.sessionToken) },
                onFailure = {
                    if (it === NotInInstanceException) {
                        InviteMessageSendResult.NotInInstance(response.sessionToken)
                    }
                    else InviteMessageSendResult.Failed(it, response.sessionToken)
                },
            )
        } finally {
            submissionGate.finish(key)
        }
    }
}

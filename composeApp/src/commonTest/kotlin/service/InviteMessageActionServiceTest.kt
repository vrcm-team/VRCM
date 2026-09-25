package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageData
import io.github.vrcmteam.vrcm.network.api.invite.data.InviteMessageType
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class InviteMessageActionServiceTest {
    @Test
    fun loadUsesTheCollectionMatchingTheAction() = runTest {
        val call = FakeInviteMessageActionCall()
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_current"))
            val service = InviteMessageActionService(call)

            assertIs<InviteMessageLoadResult.Loaded>(service.load(InviteMessageAction.Invite))
            assertIs<InviteMessageLoadResult.Loaded>(service.load(InviteMessageAction.RequestInvite))

            assertEquals(
                listOf(InviteMessageType.Message, InviteMessageType.Request),
                call.loadedTypes,
            )
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun selectedSlotAndActionArePassedToTheSendCall() = runTest {
        val call = FakeInviteMessageActionCall()
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_current"))
            val service = InviteMessageActionService(call)

            assertIs<InviteMessageSendResult.Sent>(
                service.send("usr_friend", InviteMessageAction.Invite, slot = 8)
            )
            assertIs<InviteMessageSendResult.Sent>(
                service.send("usr_friend", InviteMessageAction.RequestInvite, slot = 3)
            )

            assertEquals(
                listOf(
                    SentCall("usr_friend", InviteMessageAction.Invite, 8),
                    SentCall("usr_friend", InviteMessageAction.RequestInvite, 3),
                ),
                call.sentCalls,
            )
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun duplicateSubmissionForTheSameRecipientAndActionIsRejected() = runTest {
        val gate = CompletableDeferred<Unit>()
        val call = FakeInviteMessageActionCall(sendGate = gate)
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_current"))
            val service = InviteMessageActionService(call)
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                service.send("usr_friend", InviteMessageAction.Invite, slot = 5)
            }

            assertEquals(
                InviteMessageSendResult.InFlight,
                service.send("usr_friend", InviteMessageAction.Invite, slot = 5),
            )
            gate.complete(Unit)
            assertIs<InviteMessageSendResult.Sent>(first.await())
            assertEquals(1, call.sentCalls.size)
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun accountSwitchDiscardsALateLoadResponse() = runTest {
        val loadGate = CompletableDeferred<Unit>()
        val call = FakeInviteMessageActionCall(loadGate = loadGate)
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_first"))
            val service = InviteMessageActionService(call)
            val load = async(start = CoroutineStart.UNDISPATCHED) {
                service.load(InviteMessageAction.Invite)
            }

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_second"))
            loadGate.complete(Unit)

            assertEquals(InviteMessageLoadResult.SessionChanged, load.await())
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun renewedSessionForTheSameAccountAcceptsTheBoundResponse() = runTest {
        val call = FakeInviteMessageActionCall(renewSessionOnSend = true)
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_current"))
            val oldToken = requireNotNull(SharedFlowCentre.currentSession.value).token
            val result = assertIs<InviteMessageSendResult.Sent>(
                InviteMessageActionService(call).send(
                    "usr_friend",
                    InviteMessageAction.RequestInvite,
                    slot = 6,
                )
            )

            assertNotEquals(oldToken, result.sessionToken)
            assertEquals(SharedFlowCentre.currentSession.value?.token, result.sessionToken)
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }
}

private data class SentCall(
    val targetUserId: String,
    val action: InviteMessageAction,
    val slot: Int,
)

private class FakeInviteMessageActionCall(
    private val loadGate: CompletableDeferred<Unit>? = null,
    private val sendGate: CompletableDeferred<Unit>? = null,
    private val renewSessionOnSend: Boolean = false,
) : InviteMessageActionCall {
    val loadedTypes = mutableListOf<InviteMessageType>()
    val sentCalls = mutableListOf<SentCall>()

    override suspend fun load(
        sessionToken: AccountSessionToken,
        messageType: InviteMessageType,
    ): SessionBoundResponse<List<InviteMessageData>> {
        loadedTypes += messageType
        loadGate?.await()
        return SessionBoundResponse(
            result = Result.success(
                listOf(
                    InviteMessageData(
                        canBeUpdated = true,
                        id = "${messageType.pathValue}_0",
                        message = "Message",
                        messageType = messageType,
                        remainingCooldownMinutes = 0,
                        slot = 0,
                        updatedAt = "2026-09-01T00:00:00Z",
                    )
                )
            ),
            sessionToken = sessionToken,
        )
    }

    override suspend fun send(
        sessionToken: AccountSessionToken,
        targetUserId: String,
        action: InviteMessageAction,
        slot: Int,
    ): SessionBoundResponse<Unit> {
        sentCalls += SentCall(targetUserId, action, slot)
        sendGate?.await()
        val responseToken = if (renewSessionOnSend) {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = sessionToken.userId))
            requireNotNull(SharedFlowCentre.currentSession.value).token
        } else {
            sessionToken
        }
        return SessionBoundResponse(Result.success(Unit), responseToken)
    }
}

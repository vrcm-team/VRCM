package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class RequestInviteServiceTest {
    @Test
    fun repeatedSubmissionWaitsForTheActiveRequest() = runTest {
        val request = ControlledRequestInviteCall()
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val service = RequestInviteService(request)
            val firstSend = async(start = CoroutineStart.UNDISPATCHED) {
                service.send("usr_friend")
            }
            val sessionToken = request.nextStarted()

            assertEquals(RequestInviteResult.InFlight, service.send("usr_friend"))

            request.complete(Result.success(Unit), sessionToken)
            val result = assertIs<RequestInviteResult.Sent>(firstSend.await())
            assertEquals(sessionToken, result.sessionToken)
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun accountSwitchDiscardsTheCompletedRequest() = runTest {
        val request = ControlledRequestInviteCall()
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val send = async(start = CoroutineStart.UNDISPATCHED) {
                RequestInviteService(request).send("usr_friend")
            }
            val firstToken = request.nextStarted()

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_b"))
            request.complete(Result.success(Unit), firstToken)

            assertEquals(RequestInviteResult.SessionChanged, send.await())
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun accountSwitchAllowsTheNewAccountToSubmitTheSameRecipient() = runTest {
        val request = ControlledRequestInviteCall()
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val service = RequestInviteService(request)
            val firstSend = async(start = CoroutineStart.UNDISPATCHED) {
                service.send("usr_friend")
            }
            val firstToken = request.nextStarted()

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_b"))
            val secondSend = async(start = CoroutineStart.UNDISPATCHED) {
                service.send("usr_friend")
            }
            val secondToken = request.nextStarted()

            assertEquals("usr_b", secondToken.userId)
            request.complete(Result.success(Unit), secondToken)
            assertIs<RequestInviteResult.Sent>(secondSend.await())

            request.complete(Result.success(Unit), firstToken)
            assertEquals(RequestInviteResult.SessionChanged, firstSend.await())
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun renewedSessionForTheSameAccountCanComplete() = runTest {
        val request = ControlledRequestInviteCall()
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val send = async(start = CoroutineStart.UNDISPATCHED) {
                RequestInviteService(request).send("usr_friend")
            }
            val firstToken = request.nextStarted()

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val renewedToken = requireNotNull(SharedFlowCentre.currentSession.value).token
            assertNotEquals(firstToken, renewedToken)
            request.complete(Result.success(Unit), renewedToken)

            val result = assertIs<RequestInviteResult.Sent>(send.await())
            assertEquals(renewedToken, result.sessionToken)
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }

    @Test
    fun apiFailureRemainsAnActionableFailure() = runTest {
        val request = ControlledRequestInviteCall()
        val apiError = VRCApiException("Forbidden", 403, "not friends")
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val send = async(start = CoroutineStart.UNDISPATCHED) {
                RequestInviteService(request).send("usr_friend")
            }
            val sessionToken = request.nextStarted()

            request.complete(Result.failure(apiError), sessionToken)

            val result = assertIs<RequestInviteResult.Failed>(send.await())
            assertSame(apiError, result.error)
            assertEquals(sessionToken, result.sessionToken)
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }
}

private class ControlledRequestInviteCall : RequestInviteCall {
    private val starts = Channel<AccountSessionToken>(Channel.UNLIMITED)
    private val completions = mutableMapOf<AccountSessionToken, CompletableDeferred<SessionBoundResponse<Unit>?>>()

    override suspend fun send(
        sessionToken: AccountSessionToken,
        userId: String,
    ): SessionBoundResponse<Unit>? {
        val completion = CompletableDeferred<SessionBoundResponse<Unit>?>()
        completions[sessionToken] = completion
        starts.send(sessionToken)
        return completion.await()
    }

    suspend fun nextStarted(): AccountSessionToken = starts.receive()

    fun complete(result: Result<Unit>, sessionToken: AccountSessionToken) {
        val completion = completions.remove(sessionToken)
            ?: if (completions.size == 1) {
                completions.entries.single().let { (requestToken, pending) ->
                    completions.remove(requestToken)
                    pending
                }
            } else {
                null
            }
        completion?.complete(SessionBoundResponse(result, sessionToken))
    }
}

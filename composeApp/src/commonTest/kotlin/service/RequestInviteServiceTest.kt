package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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
            val sessionToken = request.started.await()

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
            val firstToken = request.started.await()

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_b"))
            request.complete(Result.success(Unit), firstToken)

            assertEquals(RequestInviteResult.SessionChanged, send.await())
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
            val firstToken = request.started.await()

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
            val sessionToken = request.started.await()

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
    val started = CompletableDeferred<AccountSessionToken>()
    private val completion = CompletableDeferred<SessionBoundResponse<Unit>?>()

    override suspend fun send(
        sessionToken: AccountSessionToken,
        userId: String,
    ): SessionBoundResponse<Unit>? {
        started.complete(sessionToken)
        return completion.await()
    }

    fun complete(result: Result<Unit>, sessionToken: AccountSessionToken) {
        completion.complete(SessionBoundResponse(result, sessionToken))
    }
}

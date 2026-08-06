package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoopSubmissionGateTest {
    @Test
    fun sameRecipientCannotBeSubmittedTwiceUntilRequestFinishes() {
        val gate = BoopSubmissionGate()

        assertTrue(gate.tryStart("usr_friend"))
        assertFalse(gate.tryStart("usr_friend"))

        gate.finish("usr_friend")
        assertTrue(gate.tryStart("usr_friend"))
    }

    @Test
    fun accountSwitchMakesCompletedRequestStale() = runTest {
        val request = ControlledBoopRequest()
        try {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_a"))
            val send = async(start = CoroutineStart.UNDISPATCHED) {
                BoopService(request).send("usr_friend")
            }
            val capturedToken = request.started.await()

            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_b"))
            request.complete(
                AuthenticatedBoopResponse(
                    response = Result.success(
                        VRChatResponse(
                            success = VRChatResponse.VRChatResult("ok", 200)
                        )
                    ),
                    sessionToken = capturedToken,
                )
            )

            assertEquals(BoopResult.SessionChanged, send.await())
        } finally {
            SharedFlowCentre.emitLogout()
        }
    }
}

private class ControlledBoopRequest : BoopRequest {
    val started = CompletableDeferred<AccountSessionToken>()
    private val completion = CompletableDeferred<AuthenticatedBoopResponse>()

    override suspend fun send(
        sessionToken: AccountSessionToken,
        userId: String,
        emojiId: String?,
    ): AuthenticatedBoopResponse {
        started.complete(sessionToken)
        return completion.await()
    }

    fun complete(result: AuthenticatedBoopResponse) {
        completion.complete(result)
    }
}

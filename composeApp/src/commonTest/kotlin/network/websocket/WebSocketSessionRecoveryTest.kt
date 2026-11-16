package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.koin.core.logger.EmptyLogger
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class WebSocketSessionRecoveryTest {
    @AfterTest
    fun clearSession() = runTest { SharedFlowCentre.emitLogout() }

    @Test
    fun rejectedAuthEndpointRecoversAndReconnectsWithNewSession() = runTest {
        for ((status, expectSuccess) in listOf(
            HttpStatusCode.Unauthorized to false,
            HttpStatusCode.Unauthorized to true,
            HttpStatusCode.OK to false,
        )) {
            val account = AccountDto(userId = "usr_recover", username = "recover")
            SharedFlowCentre.emitAuthenticated(account)
            val expiredSession = assertNotNull(SharedFlowCentre.currentSession.value)
            val recoveryRequest = CompletableDeferred<AccountSessionToken>()
            val reconnectedSession = CompletableDeferred<AccountSessionToken>()
            var authRequests = 0
            val client = HttpClient(MockEngine) {
                this.expectSuccess = expectSuccess
                defaultRequest { url("https://api.vrchat.cloud/api/1/") }
                install(ContentNegotiation) { json() }
                engine {
                    addHandler {
                        authRequests++
                        if (authRequests > 1) {
                            reconnectedSession.complete(assertNotNull(SharedFlowCentre.currentSession.value).token)
                            awaitCancellation()
                        }
                        respond(
                            content = """{"ok":false}""",
                            status = status,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            }
            val socket = WebSocketApi(
                apiClient = client,
                sessionRecovery = { token ->
                    recoveryRequest.complete(token)
                    SharedFlowCentre.emitAuthenticated(account)
                },
                logger = EmptyLogger(),
            )
            try {
                assertEquals(expiredSession.token, recoveryRequest.await())
                val recoveredToken = reconnectedSession.await()
                assertEquals(expiredSession.token.userId, recoveredToken.userId)
                assertFalse(expiredSession.token == recoveredToken)
                assertEquals(2, authRequests)
            } finally {
                socket.closeAndJoin()
                client.close()
                SharedFlowCentre.emitLogout()
            }
        }
    }

    @Test
    fun serverAndMalformedAuthResponsesDoNotTriggerLogin() = runTest {
        for (status in listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.Forbidden, HttpStatusCode.OK)) {
            SharedFlowCentre.emitAuthenticated(AccountDto(userId = "usr_keep"))
            val session = assertNotNull(SharedFlowCentre.currentSession.value)
            val handledFailure = CompletableDeferred<Unit>()
            var recoveryRequests = 0
            val client = HttpClient(MockEngine) {
                defaultRequest { url("https://api.vrchat.cloud/api/1/") }
                install(ContentNegotiation) { json() }
                engine {
                    addHandler {
                        respond(
                            content = """{"ok":true}""",
                            status = status,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            }
            val socket = WebSocketApi(
                apiClient = client,
                sessionRecovery = {
                    recoveryRequests++
                    handledFailure.complete(Unit)
                },
                logger = object : Logger(Level.ERROR) {
                    override fun display(level: Level, msg: String) {
                        handledFailure.complete(Unit)
                    }
                },
            )
            try {
                handledFailure.await()
            } finally {
                socket.closeAndJoin()
                client.close()
            }
            assertEquals(0, recoveryRequests)
            assertEquals(session, SharedFlowCentre.currentSession.value)
            SharedFlowCentre.emitLogout()
        }
    }
}

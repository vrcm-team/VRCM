package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_WSS_URL
import io.github.vrcmteam.vrcm.network.api.auth.data.AuthData
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*

class WebSocketApi(
    private val apiClient: HttpClient,
) {

    private var currentJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            SharedFlowCentre.authed.collect {
                currentJob?.cancelAndJoin()
                currentJob = launch { startWebSocket() }
            }
        }
        scope.launch {
            SharedFlowCentre.logout.collect {
                currentJob?.cancelAndJoin()
                currentJob = null
            }
        }
    }

    suspend fun startWebSocket() {
        retryWebSocketConnection {
            val authResponse = apiClient.get(AUTH_API_PREFIX)
            check(authResponse.status == HttpStatusCode.OK) {
                "WebSocket auth failed with HTTP ${authResponse.status.value}"
            }
            val authData = authResponse.body<AuthData>()
            val token = authData.token.takeIf { authData.ok == true && !it.isNullOrBlank() }
                ?: error("WebSocket auth response did not contain a token")
            apiClient.ws(
                urlString = VRC_WSS_URL,
                request = {
                    parameter("auth", token)
                }) {
                while (true) {
                    val othersMessage = receiveDeserialized<WebSocketEvent>()
                    SharedFlowCentre.webSocket.emit(othersMessage)
                }
            }
        }
    }
}

internal suspend fun retryWebSocketConnection(
    retryDelayMillis: Long = 5_000L,
    connect: suspend () -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        try {
            connect()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The next iteration obtains a fresh token before reconnecting.
        }
        delay(retryDelayMillis)
    }
}

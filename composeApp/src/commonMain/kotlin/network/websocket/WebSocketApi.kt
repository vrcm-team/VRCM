package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_WSS_URL
import io.github.vrcmteam.vrcm.network.api.auth.data.AuthData
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.call.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import org.koin.core.logger.Logger

class WebSocketApi(
    private val apiClient: HttpClient,
    private val logger: Logger,
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
        retryWebSocketConnection(onFailure = ::reportConnectionFailure) {
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

    private suspend fun reportConnectionFailure(error: Exception, consecutiveFailures: Int) {
        val errorType = error::class.simpleName ?: "Exception"
        logger.error(
            "WebSocket reconnect attempt $consecutiveFailures failed ($errorType): ${error.message.orEmpty()}"
        )
        if (consecutiveFailures == 1 || consecutiveFailures % USER_NOTICE_INTERVAL == 0) {
            SharedFlowCentre.toastText.emit(
                ToastText.Error(
                    "好友实时连接失败，正在重试: ${error.message.orEmpty()}"
                )
            )
        }
    }

    private companion object {
        const val USER_NOTICE_INTERVAL = 12
    }
}

internal suspend fun retryWebSocketConnection(
    retryDelayMillis: Long = 5_000L,
    onFailure: suspend (Exception, consecutiveFailures: Int) -> Unit,
    connect: suspend () -> Unit,
) {
    var consecutiveFailures = 0
    while (currentCoroutineContext().isActive) {
        try {
            connect()
            consecutiveFailures = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            consecutiveFailures++
            onFailure(e, consecutiveFailures)
        }
        delay(retryDelayMillis)
    }
}

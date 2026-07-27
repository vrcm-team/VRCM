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
import kotlinx.coroutines.*

class WebSocketApi(
    private val apiClient: HttpClient,
) {

    private var currentJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            SharedFlowCentre.authed.collect {
                currentJob?.cancel()
                currentJob = launch { startWebSocket() }
            }
        }
        scope.launch {
            SharedFlowCentre.logout.collect {
                currentJob?.cancel()
            }
        }
    }

    suspend fun startWebSocket() {
        // 参考 VRCX：从 GET /auth 获取 WebSocket token
        val authResponse = apiClient.get(AUTH_API_PREFIX)
        if (authResponse.status.value != 200) return
        val authData = authResponse.body<AuthData>()
        if (authData.ok != true || authData.token == null) return
        val token = authData.token

        try {
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
        } catch (e: Exception) {
            delay(5000L)
            startWebSocket()
        }
    }


}
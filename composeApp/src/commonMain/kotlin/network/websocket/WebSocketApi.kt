package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_API_URL
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_WSS_URL
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.parameter
import io.ktor.http.*
import kotlinx.coroutines.*

class WebSocketApi(
    private val apiClient: HttpClient,
    private val cookiesStorage: CookiesStorage,
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
    }

    suspend fun startWebSocket() {
        val authToken = cookiesStorage
                .get(Url(VRC_API_URL)).firstOrNull { it.name == DaoKeys.Cookies.AUTH_KEY }
                ?.value ?: return
        try {
            apiClient.wss(
                urlString = VRC_WSS_URL,
                request = {
                    parameter("authToken", authToken)
                }) {
                while (true) {
                    val othersMessage = receiveDeserialized<WebSocketEvent>()
                    SharedFlowCentre.webSocket.emit(othersMessage)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
//            coroutineScope {
//                this.launch { startWebSocket() }
//            }
        }
    }


}
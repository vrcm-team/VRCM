package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_API_URL
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.parameter
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import network.websocket.data.WebSocketEvent

class WebSocketApi(
    private val apiClient: HttpClient,
    private val cookiesStorage: CookiesStorage,
) {

    private var currentJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            SharedFlowCentre.authed.collect { (_, _) ->
                currentJob?.cancel()
                currentJob = launch { startWebSocket() }
            }
        }
    }

    private suspend fun startWebSocket() {
        val authToken = cookiesStorage.get(Url(VRC_API_URL)).first { it.name == DaoKeys.Cookies.AUTH_KEY }.value
        try {
            apiClient.wss(
                urlString = "wss://vrchat.com/?",
                request = {
                    parameter("authToken", authToken)
                }) {
                while (true) {
                    val othersMessage = receiveDeserialized<WebSocketEvent>()
                    SharedFlowCentre.webSocket.emit(othersMessage)
                }
            }
        }catch (e: Exception){
            e.printStackTrace()
            coroutineScope {
                this.launch { startWebSocket() }
            }
        }

    }

}
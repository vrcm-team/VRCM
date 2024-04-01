package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.subscribe.AuthCentre
import io.github.vrcmteam.vrcm.core.subscribe.WebSocketCentre
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_API_URL
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.parameter
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import network.websocket.data.WebSocketEvent

class WebSocketApi(
    private val apiClient: HttpClient,
    private val cookiesStorage: CookiesStorage,
) {

    private var currentJob: Job? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("WebSocketApiCoroutineScope"))
    init {
        AuthCentre.Subscriber.onAuthed{ _,_ ->
            with(scope) {
                currentJob = launch { startWebSocket() }
            }
        }
    }

    suspend fun startWebSocket() {
        currentJob?.cancel()
        val authToken = cookiesStorage.get(Url(VRC_API_URL)).first { it.name == DaoKeys.Cookies.AUTH_KEY }.value
        apiClient.wss(
            urlString = "wss://vrchat.com/?",
            request = {
                parameter("authToken", authToken)
            }) {
            while (true) {
                val othersMessage = receiveDeserialized<WebSocketEvent>()
                WebSocketCentre.Publisher.sendWebSocketEvent(othersMessage)
            }
        }
    }

}
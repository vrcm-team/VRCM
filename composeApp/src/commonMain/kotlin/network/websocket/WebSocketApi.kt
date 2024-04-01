package io.github.vrcmteam.vrcm.network.websocket

import io.github.vrcmteam.vrcm.core.listener.AuthedSubscriber
import io.github.vrcmteam.vrcm.core.listener.WebSocketSubscriber
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_API_URL
import io.github.vrcmteam.vrcm.storage.DaoKeys
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.parameter
import io.ktor.http.Url
import io.ktor.websocket.close
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.websocket.data.WebSocketEvent

class WebSocketApi(
    private val apiClient: HttpClient,
    private val cookiesStorage: CookiesStorage,
    private val subscribers: List<WebSocketSubscriber>
): AuthedSubscriber {

    private var closeCallback: (suspend () -> Unit)? = null

    suspend fun startWebSocket() {
        closeCallback?.invoke()
        val authToken = cookiesStorage.get(Url(VRC_API_URL)).first { it.name == DaoKeys.Cookies.AUTH_KEY }
        apiClient.wss(
            urlString = "wss://vrchat.com/?",
            request = {
                parameter("authToken", authToken)
            }) {
            closeCallback = { this.close() }
            while (true) {
                val othersMessage = receiveDeserialized<WebSocketEvent>()
                launch(Dispatchers.Default) {
                    subscribers.forEach { subscriber -> subscriber.subscribe(othersMessage) }
                }
            }
        }
    }

    override suspend fun onAuthed() {
        startWebSocket()
    }
}
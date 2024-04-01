package io.github.vrcmteam.vrcm.core.subscribe

import network.websocket.data.WebSocketEvent

object WebSocketCentre  {

    private val Subscribers = mutableListOf<(WebSocketEvent) -> Unit>()

    object  Subscriber{


        fun onWebSocketEvent(callback: (WebSocketEvent) -> Unit) {
            Subscribers.add(callback)
        }

        fun unsubscribe(callback: (WebSocketEvent) -> Unit) {
            Subscribers.remove(callback)
        }
    }

    object Publisher  {

        fun sendWebSocketEvent(webSocketEvent: WebSocketEvent) {
            Subscribers.forEach {
                it.invoke(webSocketEvent)
            }
        }

    }

}
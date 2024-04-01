package io.github.vrcmteam.vrcm.core.listener

import network.websocket.data.WebSocketEvent

interface WebSocketSubscriber {
    fun subscribe(event: WebSocketEvent)
}
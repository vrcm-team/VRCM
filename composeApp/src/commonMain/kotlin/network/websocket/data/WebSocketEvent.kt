package io.github.vrcmteam.vrcm.network.websocket.data

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketEvent(
    val type: String,
    val content: String,
)
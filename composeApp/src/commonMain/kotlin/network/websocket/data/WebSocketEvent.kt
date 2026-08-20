package io.github.vrcmteam.vrcm.network.websocket.data

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketEvent(
    val type: String,
    val content: String,
)

@Serializable
internal data class WebSocketMessage(
    val type: String? = null,
    val content: String? = null,
    val err: String? = null,
) {
    fun requireEvent(): WebSocketEvent {
        if (err != null) throw WebSocketSessionRejectedException()
        return WebSocketEvent(
            type = requireNotNull(type) { "Pipeline message did not contain an event type" },
            content = requireNotNull(content) { "Pipeline message did not contain event content" },
        )
    }
}

internal class WebSocketSessionRejectedException :
    Exception("Pipeline rejected the authenticated session")

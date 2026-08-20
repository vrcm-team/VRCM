package io.github.vrcmteam.vrcm.network.websocket.data

import kotlinx.serialization.Serializable

private const val SESSION_REJECTED_ERROR =
    "authToken doesn't correspond with an active session"

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
        err?.let { error ->
            if (error == SESSION_REJECTED_ERROR) throw WebSocketSessionRejectedException()
            throw WebSocketPipelineException(error)
        }
        return WebSocketEvent(
            type = requireNotNull(type) { "Pipeline message did not contain an event type" },
            content = requireNotNull(content) { "Pipeline message did not contain event content" },
        )
    }
}

internal class WebSocketSessionRejectedException :
    Exception("Pipeline rejected the authenticated session")

internal class WebSocketPipelineException(error: String) :
    Exception("Pipeline error: $error")

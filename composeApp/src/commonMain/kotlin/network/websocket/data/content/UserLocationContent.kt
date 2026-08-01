package io.github.vrcmteam.vrcm.network.websocket.data.content

import kotlinx.serialization.Serializable

@Serializable
data class UserLocationContent(
    val location: String,
    val travelingToLocation: String = "",
    val userId: String? = null,
)

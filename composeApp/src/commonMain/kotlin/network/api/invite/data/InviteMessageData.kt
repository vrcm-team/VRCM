package io.github.vrcmteam.vrcm.network.api.invite.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Server-supported collections of reusable invite messages. */
@Serializable
enum class InviteMessageType(val pathValue: String) {
    @SerialName("message")
    Message("message"),

    @SerialName("response")
    Response("response"),

    @SerialName("request")
    Request("request"),

    @SerialName("requestResponse")
    RequestResponse("requestResponse"),
}

/** A single reusable message slot and its server-managed cooldown state. */
@Serializable
data class InviteMessageData(
    val canBeUpdated: Boolean,
    val id: String,
    val message: String,
    val messageType: InviteMessageType,
    val remainingCooldownMinutes: Int,
    val slot: Int,
    val updatedAt: String,
)
@Serializable
internal data class UpdateInviteMessageRequest(
    val message: String,
)

package io.github.vrcmteam.vrcm.network.api.invite.data

import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InviteMyselfData(
    @SerialName("created_at")
    val createdAt: String,
    val details: Details,
    val id: String,
    val message: String,
    val senderUserId: String,
    val senderUsername: String,
    val receiverUserId: String?,
    val type: NotificationType,
) {

    @Serializable
    data class Details(
        val worldId: String,
        val worldName: String,
    )

}
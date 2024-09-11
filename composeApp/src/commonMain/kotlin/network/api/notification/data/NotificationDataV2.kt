package io.github.vrcmteam.vrcm.network.api.notification.data

import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDataV2 (
    @SerialName("created_at")
    val createdAt: String,
    val details: String,
    val id: String,
    val message: String,
    val seen: Boolean,
    val senderUserId: String,
    val receiverUserId: String?,
    val type: NotificationType
)
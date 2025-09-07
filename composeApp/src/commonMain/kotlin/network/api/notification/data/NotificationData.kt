package io.github.vrcmteam.vrcm.network.api.notification.data

import kotlinx.serialization.Serializable

@Serializable
data class NotificationData(
    val canDelete: Boolean,
    val category: String,
    val createdAt: String,
    val data: Data,
    val expiresAt: String,
    val expiryAfterSeen: Int?,
    val id: String,
    val ignoreDND: Boolean,
    val imageUrl: String?,
    val isSystem: Boolean,
    val link: String?,
    val linkText: String?,
    val linkTextKey: String?,
    val message: String,
    val messageKey: String?,
    val receiverUserId: String,
    val relatedNotificationsId: String?,
    val requireSeen: Boolean,
    val responses: List<ResponseData>,
    val seen: Boolean,
    val senderUserId: String?,
    val senderUsername: String?,
    val title: String?,
    val titleKey: String?,
    val type: String,
    val updatedAt: String,
    val version: Int
){
    @Serializable
    data class Data(
        val announcementTitle: String?,
        val groupName: String?,
    )
}

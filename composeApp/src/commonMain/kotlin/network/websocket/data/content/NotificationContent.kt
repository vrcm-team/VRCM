package io.github.vrcmteam.vrcm.network.websocket.data.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Shared subset of legacy `notification` and current `notification-v2` Pipeline payloads. */
@Serializable
data class NotificationContent(
    val id: String,
    val version: Int = 0,
    val type: String,
    val senderUserId: String? = null,
    val senderUsername: String? = null,
    val relatedNotificationsId: String? = null,
    val message: String = "",
    val title: String? = null,
    val data: Data = Data(),
    val details: Data? = null,
) {
    @Serializable
    data class Data(
        val announcementTitle: String? = null,
        val boopingUserDisplayName: String? = null,
        val groupName: String? = null,
        val emojiId: String? = null,
    )
}

/** Partial update emitted for an existing `notification-v2` item. */
@Serializable
data class NotificationV2UpdateContent(
    val id: String,
    val version: Int,
    val updates: JsonObject = JsonObject(emptyMap()),
)

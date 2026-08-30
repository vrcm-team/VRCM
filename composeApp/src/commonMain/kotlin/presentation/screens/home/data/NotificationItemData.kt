package io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2

data class NotificationItemData(
    val id: String,
    val imageUrl: String,
    val title: String?,
    val message: String,
    val createdAt: String,
    val senderUserId: String,
    val link: String?,
    val type: String,
    val actions: List<ActionData>,
    val seen: Boolean = false,
    val canDelete: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val announcementTitle: String? = null,
    /** The selected default or custom Boop emoji identifier, when VRChat supplies it. */
    val boopEmojiId: String? = null,
    /** Pipeline events can reference this inbox item through a different notification ID. */
    val relatedNotificationId: String? = null,
) {
    /** The notification sender used by sender-specific actions such as opening a profile or replying to a Boop. */
    val senderId: String?
        get() = senderUserId.trim().takeIf { it.isNotEmpty() }

    /** The VRChat user targeted by a `user:usr_...` notification link. */
    val linkedUserId: String?
        get() = link
            ?.takeIf { it.startsWith("user:") }
            ?.removePrefix("user:")
            ?.takeIf { it.isNotBlank() }

    data class ActionData(
        val data: String,
        val type: String,
        val icon: String = "",
        val label: String = "",
    )

    constructor(n: NotificationData) : this(
        id = n.id,
        imageUrl = (n.imageUrl ?: n.details?.imageUrl ?: n.data.imageUrl).orEmpty(),
        title = n.title,
        message = n.message,
        createdAt = n.createdAt,
        senderUserId = n.senderUserId.orEmpty(),
        link = n.link,
        type = n.type,
        actions = n.responses.map { responses ->
            ActionData(
                data = responses.responseData,
                type = responses.type,
                icon = responses.icon,
                label = responses.text,
            )
        },
        seen = n.seen,
        canDelete = n.canDelete,
        groupId = n.groupId ?: n.details?.groupId ?: n.data.groupId,
        groupName = n.details?.groupName ?: n.data.groupName,
        announcementTitle = n.details?.announcementTitle ?: n.data.announcementTitle,
        relatedNotificationId = n.relatedNotificationsId,
        boopEmojiId = n.details?.emojiId ?: n.data.emojiId,
    )

    constructor(
        n: NotificationDataV2,
        imageUrl: String,
        title: String,
        actions: List<ActionData>,
    ) : this(
        id = n.id,
        imageUrl = imageUrl,
        title = title,
        message = n.message,
        createdAt = n.createdAt,
        senderUserId = n.senderUserId,
        link = "user:${n.senderUserId}",
        type = n.type.value,
        actions = actions,
        seen = n.seen,
        canDelete = true,
    )

}

/** Number of notifications that still need the user's attention. */
val List<NotificationItemData>.unreadCount: Int
    get() = count { !it.seen }

/** Resolves either the inbox ID or the related Pipeline event ID to its rendered item. */
internal fun List<NotificationItemData>.indexOfNotificationTarget(targetId: String?): Int {
    val requestedId = targetId?.takeIf(String::isNotBlank) ?: return -1
    return indexOfFirst { item ->
        item.id == requestedId || item.relatedNotificationId == requestedId
    }
}

internal enum class NotificationResponseTarget {
    BOOP_USER_API,
    NOTIFICATION_API,
}

internal fun NotificationItemData.responseTarget(
    action: NotificationItemData.ActionData,
): NotificationResponseTarget =
    if (type == "boop" && action.icon.equals("reply", ignoreCase = true)) {
        NotificationResponseTarget.BOOP_USER_API
    } else {
        NotificationResponseTarget.NOTIFICATION_API
    }

package io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
import io.github.vrcmteam.vrcm.network.api.notification.data.resolveNotificationGroupId
import io.github.vrcmteam.vrcm.service.OfficialLinkType
import io.github.vrcmteam.vrcm.service.parseOfficialId
import io.github.vrcmteam.vrcm.service.parseOfficialLink
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** API family that owns a notification and all mutations performed on it. */
enum class NotificationSource {
    PIPELINE,
    LEGACY,
}

data class NotificationItemData(
    val id: String,
    val source: NotificationSource,
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
    /** Label supplied for the notification's top-level link. */
    val linkText: String? = null,
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
        source = NotificationSource.PIPELINE,
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
        groupId = resolveNotificationGroupId(
            n.link,
            n.groupId,
            n.details?.groupId,
            n.data.groupId,
            n.details?.ownerId,
            n.data.ownerId,
        ),
        groupName = n.details?.groupName ?: n.data.groupName,
        announcementTitle = n.details?.announcementTitle ?: n.data.announcementTitle,
        relatedNotificationId = n.relatedNotificationsId,
        boopEmojiId = n.details?.emojiId ?: n.data.emojiId,
        linkText = n.linkText,
    )

    constructor(
        n: NotificationDataV2,
        imageUrl: String,
        title: String,
        actions: List<ActionData>,
    ) : this(
        id = n.id,
        source = NotificationSource.LEGACY,
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
    NAVIGATION_LINK,
}

internal enum class NotificationReadTarget {
    PIPELINE_SEE,
    LEGACY_SEE,
}

internal val NotificationItemData.readTarget: NotificationReadTarget
    get() = when (source) {
        NotificationSource.PIPELINE -> NotificationReadTarget.PIPELINE_SEE
        NotificationSource.LEGACY -> NotificationReadTarget.LEGACY_SEE
    }

internal data class NotificationInboxState(
    val pipeline: List<NotificationItemData> = emptyList(),
    val legacy: List<NotificationItemData> = emptyList(),
    private val consumed: Set<NotificationIdentity> = emptySet(),
    private val seenNotifications: Set<NotificationIdentity> = emptySet(),
) {
    fun replace(source: NotificationSource, items: List<NotificationItemData>): NotificationInboxState {
        // Seen is monotonic within a session, so a stale refresh cannot undo a read mutation.
        val mergedSeen = seenNotifications + items.filter { it.seen }.map { it.identity }
        val visible = items
            .filterNot { it.identity in consumed }
            .map { item ->
                if (!item.seen && item.identity in mergedSeen) item.copy(seen = true) else item
            }
        return when (source) {
            NotificationSource.PIPELINE -> copy(pipeline = visible, seenNotifications = mergedSeen)
            NotificationSource.LEGACY -> copy(legacy = visible, seenNotifications = mergedSeen)
        }
    }

    fun consume(item: NotificationItemData): NotificationInboxState {
        val identity = item.identity
        return when (item.source) {
            NotificationSource.PIPELINE -> copy(
                pipeline = pipeline.filterNot { it.identity == identity },
                consumed = consumed + identity,
            )
            NotificationSource.LEGACY -> copy(
                legacy = legacy.filterNot { it.identity == identity },
                consumed = consumed + identity,
            )
        }
    }

    fun markSeen(item: NotificationItemData): NotificationInboxState {
        val identity = item.identity
        return when (item.source) {
            NotificationSource.PIPELINE -> copy(
                pipeline = pipeline.map { current ->
                    if (current.identity == identity) current.copy(seen = true) else current
                },
                seenNotifications = seenNotifications + identity,
            )
            NotificationSource.LEGACY -> copy(
                legacy = legacy.map { current ->
                    if (current.identity == identity) current.copy(seen = true) else current
                },
                seenNotifications = seenNotifications + identity,
            )
        }
    }
}

internal data class NotificationIdentity(
    val source: NotificationSource,
    val id: String,
    val relatedNotificationId: String?,
) {
    val stableKey: String
        get() = "${source.name}:$id:${relatedNotificationId.orEmpty()}"
}

/** Stable identity shared by inbox state, pending mutations, and rendered item state. */
internal val NotificationItemData.identity: NotificationIdentity
    get() = NotificationIdentity(
        source = source,
        id = id,
        relatedNotificationId = relatedNotificationId?.takeIf(String::isNotBlank),
    )

internal fun NotificationItemData.responseTarget(
    action: NotificationItemData.ActionData,
): NotificationResponseTarget =
    if (action.type.equals("link", ignoreCase = true)) {
        NotificationResponseTarget.NAVIGATION_LINK
    } else if (type.equals("boop", ignoreCase = true) && action.icon.equals("reply", ignoreCase = true)) {
        NotificationResponseTarget.BOOP_USER_API
    } else {
        NotificationResponseTarget.NOTIFICATION_API
    }

/** Resolves the server reply response, with a Users API fallback when a Boop omits responses. */
internal val NotificationItemData.boopReplyAction: NotificationItemData.ActionData?
    get() {
        if (!type.equals("boop", ignoreCase = true)) return null
        return actions.firstOrNull { responseTarget(it) == NotificationResponseTarget.BOOP_USER_API }
            ?: NotificationItemData.ActionData(data = "", type = "boop", icon = "reply")
    }

internal sealed interface NotificationActionTarget {
    data class User(val id: String) : NotificationActionTarget
    data class Group(val id: String) : NotificationActionTarget
    data class World(val id: String) : NotificationActionTarget
    data class Avatar(val id: String) : NotificationActionTarget
    data class External(val url: String, val host: String) : NotificationActionTarget
}

/** Responses plus the current notification's independent top-level link, without duplicates. */
internal val NotificationItemData.displayActions: List<NotificationItemData.ActionData>
    get() {
        if (source != NotificationSource.PIPELINE) return actions
        val topLevelLink = link?.trim().takeUnless { it.isNullOrEmpty() } ?: return actions
        if (actions.any { it.type.equals("link", ignoreCase = true) && it.data.trim() == topLevelLink }) {
            return actions
        }
        return actions + NotificationItemData.ActionData(
            data = topLevelLink,
            type = "link",
            icon = "link",
            label = linkText.orEmpty(),
        )
    }

internal fun NotificationItemData.actionTarget(
    action: NotificationItemData.ActionData,
): NotificationActionTarget? {
    if (!action.type.equals("link", ignoreCase = true)) return null
    val value = action.data.trim().takeIf(String::isNotEmpty) ?: return null
    val target = prefixedNotificationTarget(value) ?: parseOfficialLink(value)
    if (target == null) return safeExternalTarget(value)
    return when (target.type) {
        OfficialLinkType.User -> NotificationActionTarget.User(target.id)
        OfficialLinkType.Group -> NotificationActionTarget.Group(target.id)
        OfficialLinkType.World -> NotificationActionTarget.World(target.id)
        OfficialLinkType.Avatar -> NotificationActionTarget.Avatar(target.id)
    }
}

private fun prefixedNotificationTarget(value: String) =
    value.substringBefore(':').lowercase().let { prefix ->
        val payload = value.substringAfter(':', missingDelimiterValue = "")
        val id = when (prefix) {
            "event" -> payload.split(',', limit = 2)
                .takeIf { parts ->
                    parts.size == 2 && Regex("cal_[A-Za-z0-9-]+").matches(parts[1])
                }
                ?.first()
            "user", "group", "world", "avatar" -> payload
            else -> null
        } ?: return@let null
        val expectedType = when (prefix) {
            "user" -> OfficialLinkType.User
            "world" -> OfficialLinkType.World
            "avatar" -> OfficialLinkType.Avatar
            else -> OfficialLinkType.Group
        }
        parseOfficialId(id)?.takeIf { it.type == expectedType }
    }

private fun safeExternalTarget(value: String): NotificationActionTarget.External? {
    if (value.any(Char::isISOControl)) return null
    val url = runCatching { Url(value) }.getOrNull() ?: return null
    if (url.protocol != URLProtocol.HTTPS || url.host.isBlank()) return null
    if (url.user != null || url.password != null) return null
    return NotificationActionTarget.External(url = url.toString(), host = url.host)
}

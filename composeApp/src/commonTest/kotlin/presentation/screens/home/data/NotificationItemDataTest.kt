package io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationItemDataTest {
    @Test
    fun currentNotificationKeepsUnreadAndGroupPresentationMetadata() {
        val item = NotificationItemData(
            NotificationData(
                canDelete = true,
                category = "group",
                createdAt = "2026-08-30T00:00:00Z",
                data = NotificationData.Data(
                    announcementTitle = "Weekly meetup",
                    groupName = "VRCM Club",
                    groupId = "grp_vrcm",
                ),
                expiresAt = "2026-09-30T00:00:00Z",
                expiryAfterSeen = null,
                id = "not_group",
                ignoreDND = false,
                imageUrl = "https://example.com/group.png",
                isSystem = false,
                link = null,
                linkText = null,
                linkTextKey = null,
                message = "Join us",
                messageKey = null,
                receiverUserId = "usr_receiver",
                relatedNotificationsId = "pipeline_event",
                requireSeen = true,
                responses = emptyList(),
                seen = false,
                senderUserId = null,
                senderUsername = null,
                title = null,
                titleKey = null,
                type = "group.announcement",
                updatedAt = "2026-08-30T00:00:00Z",
                version = 2,
            ),
        )

        assertEquals(false, item.seen)
        assertEquals(true, item.canDelete)
        assertEquals("grp_vrcm", item.groupId)
        assertEquals("VRCM Club", item.groupName)
        assertEquals("Weekly meetup", item.announcementTitle)
        assertEquals("pipeline_event", item.relatedNotificationId)
    }

    @Test
    fun legacyFriendRequestSeenStateParticipatesInUnreadBadge() {
        val unread = friendRequest("not_unread", seen = false)
        val read = friendRequest("not_read", seen = true)

        assertEquals(1, listOf(unread, read).unreadCount)
    }

    private fun friendRequest(id: String, seen: Boolean) = NotificationItemData(
        n = NotificationDataV2(
            createdAt = "2026-08-30T00:00:00Z",
            details = "",
            id = id,
            message = "Friend request",
            seen = seen,
            senderUserId = "usr_sender",
            receiverUserId = "usr_receiver",
            type = NotificationType.FriendRequest,
        ),
        imageUrl = "",
        title = "Sender",
        actions = emptyList(),
    )
}

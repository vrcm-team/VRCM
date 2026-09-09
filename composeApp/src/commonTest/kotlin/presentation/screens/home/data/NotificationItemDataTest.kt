package io.github.vrcmteam.vrcm.presentation.screens.home.data

import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
import io.github.vrcmteam.vrcm.network.api.notification.data.ResponseData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        assertEquals(NotificationSource.PIPELINE, item.source)
        assertEquals(NotificationReadTarget.PIPELINE_SEE, item.readTarget)
    }

    @Test
    fun legacyFriendRequestSeenStateParticipatesInUnreadBadge() {
        val unread = friendRequest("not_unread", seen = false)
        val read = friendRequest("not_read", seen = true)

        assertEquals(1, listOf(unread, read).unreadCount)
        assertEquals(NotificationSource.LEGACY, unread.source)
        assertEquals(NotificationReadTarget.LEGACY_SEE, unread.readTarget)
        assertEquals(emptyList(), unread.displayActions)
    }

    @Test
    fun photoResponseSupportIsLimitedToInviteNotificationFamilies() {
        val invite = NotificationItemData(pipelineNotification(type = "invite"))
        val requestInvite = NotificationItemData(pipelineNotification(type = "requestInvite"))
        val inviteResponse = NotificationItemData(pipelineNotification(type = "inviteResponse"))
        val boop = NotificationItemData(pipelineNotification(type = "boop"))

        assertEquals(true, invite.supportsInvitePhotoResponse)
        assertEquals(true, requestInvite.supportsInvitePhotoResponse)
        assertEquals(false, inviteResponse.supportsInvitePhotoResponse)
        assertEquals(false, boop.supportsInvitePhotoResponse)
    }

    @Test
    fun groupTargetFallsBackToOwnerIdAndOfficialLink() {
        val ownerItem = NotificationItemData(
            pipelineNotification(details = NotificationData.Data(ownerId = "grp_owner")),
        )
        val linkItem = NotificationItemData(
            pipelineNotification(link = "https://vrchat.com/home/group/grp_link"),
        )

        assertEquals("grp_owner", ownerItem.groupId)
        assertEquals("grp_link", linkItem.groupId)
    }

    @Test
    fun currentNotificationMapsResponsesAndAddsTopLevelLinkOnce() {
        val response = Json.decodeFromString<ResponseData>(
            """{"data":"group:grp_123","icon":"check","text":"Open group","textKey":null,"type":"link"}""",
        )
        val item = NotificationItemData(
            pipelineNotification(
                link = "group:grp_123",
                linkText = "View group",
                responses = listOf(response),
            ),
        )
        val linkOnly = NotificationItemData(
            pipelineNotification(
                link = "event:grp_events,cal_weekly",
                linkText = "View event",
            ),
        )

        assertNull(response.textKey)
        assertEquals(
            listOf(NotificationItemData.ActionData("group:grp_123", "link", "check", "Open group")),
            item.displayActions,
        )
        assertEquals(
            NotificationItemData.ActionData("event:grp_events,cal_weekly", "link", "link", "View event"),
            linkOnly.displayActions.single(),
        )
    }

    @Test
    fun currentNotificationDeduplicatesEquivalentInternalLinkRepresentations() {
        val response = Json.decodeFromString<ResponseData>(
            """{"data":"group:grp_123","icon":"check","text":"Open group","textKey":null,"type":"link"}""",
        )
        val item = NotificationItemData(
            pipelineNotification(
                link = "https://vrchat.com/home/group/grp_123",
                linkText = "View group",
                responses = listOf(response),
            ),
        )

        assertEquals(
            listOf(NotificationItemData.ActionData("group:grp_123", "link", "check", "Open group")),
            item.displayActions,
        )
    }

    @Test
    fun currentNotificationDeduplicatesNormalizedExternalLinks() {
        val response = Json.decodeFromString<ResponseData>(
            """{"data":"https://example.com","icon":"link","text":"Open site","textKey":null,"type":"link"}""",
        )
        val item = NotificationItemData(
            pipelineNotification(
                link = "https://example.com/",
                linkText = "View site",
                responses = listOf(response),
            ),
        )

        assertEquals(
            listOf(NotificationItemData.ActionData("https://example.com", "link", "link", "Open site")),
            item.displayActions,
        )
    }

    @Test
    fun linkActionParsesInternalAndSafeExternalTargets() {
        val item = NotificationItemData(pipelineNotification())

        assertEquals(
            NotificationActionTarget.Group("grp_123"),
            item.actionTarget(NotificationItemData.ActionData("group:grp_123", "link")),
        )
        assertEquals(
            NotificationResponseTarget.NAVIGATION_LINK,
            item.responseTarget(NotificationItemData.ActionData("group:grp_123", "link")),
        )
        assertEquals(
            NotificationActionTarget.User("usr_123"),
            item.actionTarget(NotificationItemData.ActionData("user:usr_123", "link")),
        )
        assertEquals(
            NotificationActionTarget.World("wrld_123"),
            item.actionTarget(NotificationItemData.ActionData("world:wrld_123", "link")),
        )
        assertEquals(
            NotificationActionTarget.Avatar("avtr_123"),
            item.actionTarget(NotificationItemData.ActionData("avatar:avtr_123", "link")),
        )
        assertEquals(
            NotificationActionTarget.Group("grp_events"),
            item.actionTarget(NotificationItemData.ActionData("event:grp_events,cal_weekly", "link")),
        )
        assertEquals(
            NotificationActionTarget.Group("grp_official"),
            item.actionTarget(
                NotificationItemData.ActionData(
                    "https://vrchat.com/home/group/grp_official",
                    "link",
                ),
            ),
        )
        assertEquals(
            NotificationActionTarget.External("https://example.com/group/grp_public", "example.com"),
            item.actionTarget(
                NotificationItemData.ActionData("https://example.com/group/grp_public", "link"),
            ),
        )
        assertNull(
            item.copy(link = "https://vrchat.com/home/group/grp_fallback")
                .actionTarget(NotificationItemData.ActionData("unsupported:value", "link")),
        )
        assertNull(item.actionTarget(NotificationItemData.ActionData("group:not-a-group", "link")))
        assertNull(item.actionTarget(NotificationItemData.ActionData("user:grp_mismatch", "link")))
        assertNull(item.actionTarget(NotificationItemData.ActionData("event:grp_events,invalid", "link")))
        assertNull(item.actionTarget(NotificationItemData.ActionData("http://example.com", "link")))
        assertNull(item.actionTarget(NotificationItemData.ActionData("https://user:secret@example.com", "link")))
        assertNull(item.actionTarget(NotificationItemData.ActionData("javascript:alert(1)", "link")))
    }

    private fun pipelineNotification(
        details: NotificationData.Data? = null,
        link: String? = null,
        linkText: String? = null,
        responses: List<ResponseData> = emptyList(),
        type: String = "group.announcement",
    ) = NotificationData(
        canDelete = true,
        category = "group",
        createdAt = "2026-08-30T00:00:00Z",
        data = NotificationData.Data(),
        details = details,
        expiresAt = "2026-09-30T00:00:00Z",
        expiryAfterSeen = null,
        id = "not_group_target",
        ignoreDND = false,
        imageUrl = null,
        isSystem = false,
        link = link,
        linkText = linkText,
        linkTextKey = null,
        message = "Join us",
        messageKey = null,
        receiverUserId = "usr_receiver",
        relatedNotificationsId = null,
        requireSeen = true,
        responses = responses,
        seen = false,
        senderUserId = null,
        senderUsername = null,
        title = null,
        titleKey = null,
        type = type,
        updatedAt = "2026-08-30T00:00:00Z",
        version = 2,
    )

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

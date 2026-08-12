package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupNotificationTypeTest {
    @Test
    fun supportedGroupAnnouncementsEventsAndManagementMessagesAreIncluded() {
        listOf(
            "groupChange",
            "group.announcement",
            "group.event.created",
            "group.event.starting",
            "group.informative",
            "group.joinRequest",
            "group.transfer",
            "group.queueReady",
        ).forEach { type ->
            assertTrue(isGroupNotificationType("  $type "))
        }
    }

    @Test
    fun groupInvitesAndUnrecognizedTypesRemainExcluded() {
        assertFalse(isGroupNotificationType("group.invite"))
        assertFalse(isGroupNotificationType("invite"))
        assertFalse(isGroupNotificationType("group.event.cancelled"))
        assertFalse(isGroupNotificationType(""))
    }
}

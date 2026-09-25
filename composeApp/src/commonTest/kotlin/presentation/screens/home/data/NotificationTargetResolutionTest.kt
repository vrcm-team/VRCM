package io.github.vrcmteam.vrcm.presentation.screens.home.data

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTargetResolutionTest {
    @Test
    fun resolvesPipelineRelatedIdToInboxItemIndex() {
        val notifications = listOf(
            notification(id = "not_newest"),
            notification(id = "not_inbox", relatedId = "not_pipeline"),
            notification(id = "not_oldest"),
        )

        assertEquals(0, notifications.indexOfNotificationTarget("not_newest"))
        assertEquals(1, notifications.indexOfNotificationTarget("not_pipeline"))
        assertEquals(-1, notifications.indexOfNotificationTarget("not_missing"))
    }

    private fun notification(
        id: String,
        relatedId: String? = null,
    ) = NotificationItemData(
        id = id,
        source = NotificationSource.PIPELINE,
        imageUrl = "",
        title = null,
        message = "",
        createdAt = "2026-08-12T00:00:00Z",
        senderUserId = "",
        link = null,
        type = "group.announcement",
        actions = emptyList(),
        relatedNotificationId = relatedId,
    )
}

package io.github.vrcmteam.vrcm.presentation.screens.notification

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import io.github.vrcmteam.vrcm.core.shared.AccountWebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.WebSocketEvent
import io.github.vrcmteam.vrcm.network.websocket.data.type.NotificationEvents
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationInboxState
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationSource
import io.github.vrcmteam.vrcm.service.BoopResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationInboxStateTest {
    @Test
    fun successfulActionConsumesOnlyItsSourceAndRejectsStaleRefreshEntry() {
        val pipeline = notification("not_shared", NotificationSource.PIPELINE)
        val legacy = notification("not_shared", NotificationSource.LEGACY)
        val initial = NotificationInboxState()
            .replace(NotificationSource.PIPELINE, listOf(pipeline))
            .replace(NotificationSource.LEGACY, listOf(legacy))

        val consumed = initial.afterNotificationAction(pipeline, Result.success(Unit))
        val afterStaleRefresh = consumed.replace(NotificationSource.PIPELINE, listOf(pipeline))

        assertEquals(emptyList(), afterStaleRefresh.pipeline)
        assertEquals(listOf(legacy), afterStaleRefresh.legacy)
    }

    @Test
    fun consumedAggregateRejectsStaleRelationButShowsNewUnderlyingEvent() {
        val oldEvent = notification(
            id = "not_aggregate",
            source = NotificationSource.PIPELINE,
            relatedNotificationId = "not_event_a",
        )
        val newEvent = notification(
            id = "not_aggregate",
            source = NotificationSource.PIPELINE,
            relatedNotificationId = "not_event_b",
        )
        val consumed = NotificationInboxState()
            .replace(NotificationSource.PIPELINE, listOf(oldEvent))
            .consume(oldEvent)

        val refreshed = consumed.replace(
            NotificationSource.PIPELINE,
            listOf(oldEvent, newEvent),
        )

        assertEquals(listOf(newEvent), refreshed.pipeline)
    }

    @Test
    fun blankAndMissingRelationShareStableIdentity() {
        val missingRelation = notification("not_without_relation", NotificationSource.PIPELINE)
        val blankRelation = notification(
            id = "not_without_relation",
            source = NotificationSource.PIPELINE,
            relatedNotificationId = "   ",
        )
        val consumed = NotificationInboxState()
            .replace(NotificationSource.PIPELINE, listOf(missingRelation))
            .consume(missingRelation)

        val refreshed = consumed.replace(NotificationSource.PIPELINE, listOf(blankRelation))

        assertEquals(emptyList(), refreshed.pipeline)
    }

    @Test
    fun markSeenChangesOnlyTheMatchingUnderlyingEvent() {
        val oldEvent = notification(
            id = "not_aggregate",
            source = NotificationSource.PIPELINE,
            relatedNotificationId = "not_event_a",
        )
        val newEvent = notification(
            id = "not_aggregate",
            source = NotificationSource.PIPELINE,
            relatedNotificationId = "not_event_b",
        )
        val initial = NotificationInboxState().replace(
            NotificationSource.PIPELINE,
            listOf(oldEvent, newEvent),
        )

        val seen = initial.markSeen(oldEvent)

        assertEquals(listOf(true, false), seen.pipeline.map { it.seen })
    }

    @Test
    fun failedActionPreservesCurrentNotification() {
        val item = notification("not_failed", NotificationSource.PIPELINE)
        val initial = NotificationInboxState().replace(NotificationSource.PIPELINE, listOf(item))

        val afterFailure = initial.afterNotificationAction(
            item,
            Result.failure<Unit>(IllegalStateException("request failed")),
        )

        assertEquals(listOf(item), afterFailure.pipeline)
    }

    @Test
    fun onlySentBoopConsumesNotification() {
        val item = notification("not_boop", NotificationSource.PIPELINE)
        val initial = NotificationInboxState().replace(NotificationSource.PIPELINE, listOf(item))
        val retainedResults = listOf<BoopResult>(
            BoopResult.Cooldown,
            BoopResult.Disabled,
            BoopResult.InFlight,
            BoopResult.SessionChanged,
            BoopResult.Failed(IllegalStateException("failed")),
        )

        retainedResults.forEach { result ->
            assertEquals(listOf(item), initial.afterBoopResult(item, result).pipeline)
        }
        assertEquals(emptyList(), initial.afterBoopResult(item, BoopResult.Sent).pipeline)
    }

    @Test
    fun notificationEventRefreshesOnlyItsCurrentAccount() {
        val current = AccountSessionToken("usr_current", 2)
        val old = AccountSessionToken("usr_old", 1)
        val newNotification = AccountWebSocketEvent(
            token = current,
            event = WebSocketEvent(NotificationEvents.NotificationV2.typeName, "{}"),
        )
        val staleNotification = AccountWebSocketEvent(
            token = old,
            event = WebSocketEvent(NotificationEvents.Notification.typeName, "{}"),
        )
        val unrelated = AccountWebSocketEvent(
            token = current,
            event = WebSocketEvent("friend-online", "{}"),
        )

        assertEquals(current, newNotification.notificationRefreshToken(current))
        assertNull(staleNotification.notificationRefreshToken(current))
        assertNull(unrelated.notificationRefreshToken(current))
    }

    @Test
    fun everyNotificationMutationEventRequiresInboxRefresh() {
        val current = AccountSessionToken("usr_current", 2)
        val eventTypes = listOf(
            NotificationEvents.NotificationV2Update.typeName,
            NotificationEvents.ResponseNotification.typeName,
            NotificationEvents.SeeNotification.typeName,
            NotificationEvents.HideNotification.typeName,
            NotificationEvents.ClearNotification.typeName,
        )

        eventTypes.forEach { type ->
            val event = AccountWebSocketEvent(current, WebSocketEvent(type, "{}"))
            assertEquals(current, event.notificationRefreshToken(current), type)
        }
    }

    private fun notification(
        id: String,
        source: NotificationSource,
        relatedNotificationId: String? = null,
    ) = NotificationItemData(
        id = id,
        source = source,
        imageUrl = "",
        title = null,
        message = "",
        createdAt = "2026-08-30T00:00:00Z",
        senderUserId = "usr_sender",
        link = null,
        type = "boop",
        actions = emptyList(),
        relatedNotificationId = relatedNotificationId,
    )
}

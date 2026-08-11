package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationDeliveryTrackerTest {
    @Test
    fun legacyAndRelatedV2PayloadRepresentOneAlertInEitherOrder() {
        val legacyFirst = NotificationDeliveryTracker()
        assertTrue(legacyFirst.shouldDeliverLegacy("not_event"))
        assertFalse(
            legacyFirst.shouldDeliverV2(
                id = "not_inbox",
                version = 1,
                relatedId = "not_event",
            ),
        )

        val v2First = NotificationDeliveryTracker()
        assertTrue(
            v2First.shouldDeliverV2(
                id = "not_inbox",
                version = 1,
                relatedId = "not_event",
            ),
        )
        assertFalse(v2First.shouldDeliverLegacy("not_event"))
    }

    @Test
    fun newRelatedEventOnAggregatedItemAlertsAgain() {
        val tracker = NotificationDeliveryTracker()
        tracker.shouldDeliverV2(
            id = "not_inbox",
            version = 3,
            relatedId = "not_old",
            seedOnly = true,
        )

        assertFalse(
            tracker.shouldDeliverV2(
                id = "not_inbox",
                version = 4,
                relatedId = "not_old",
            ),
        )
        assertTrue(
            tracker.shouldDeliverV2(
                id = "not_inbox",
                version = 5,
                relatedId = "not_new",
            ),
        )
        assertFalse(tracker.shouldDeliverLegacy("not_new"))
    }

    @Test
    fun staleRefreshCannotRestoreAnAlreadyHandledRelation() {
        val tracker = NotificationDeliveryTracker()
        tracker.shouldDeliverV2("not_inbox", 5, "not_current", seedOnly = true)

        assertFalse(tracker.shouldDeliverV2("not_inbox", 4, "not_stale"))
        assertTrue(tracker.shouldDeliverV2("not_inbox", 6, "not_next"))
    }

    @Test
    fun pipelineEventStillAlertsWhenStartupSeedSawItFirst() {
        val tracker = NotificationDeliveryTracker()
        tracker.shouldDeliverV2(
            id = "not_inbox",
            version = 1,
            relatedId = "not_event",
            seedOnly = true,
        )

        assertTrue(
            tracker.shouldDeliverV2(
                id = "not_inbox",
                version = 1,
                relatedId = "not_event",
                isPipelineEvent = true,
            ),
        )
        assertFalse(
            tracker.shouldDeliverV2(
                id = "not_inbox",
                version = 1,
                relatedId = "not_event",
                isPipelineEvent = true,
            ),
        )
    }
}

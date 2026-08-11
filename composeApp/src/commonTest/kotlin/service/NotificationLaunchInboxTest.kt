package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationLaunchInboxTest {
    @Test
    fun staleConsumptionDoesNotDiscardANewerNotificationTap() {
        val inbox = NotificationLaunchInbox()
        inbox.submit(NotificationLaunchDestination.UserProfile, "usr_first")
        val first = inbox.pendingRequest.value!!

        inbox.submit(NotificationLaunchDestination.NotificationCenter, "notification_second")
        val second = inbox.pendingRequest.value!!
        inbox.consume(first)

        assertEquals(second, inbox.pendingRequest.value)
        inbox.consume(second)
        assertNull(inbox.pendingRequest.value)
    }
}

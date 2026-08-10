package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialLinkInboxTest {
    @Test
    fun staleConsumptionDoesNotDiscardANewerExternalLink() {
        val inbox = OfficialLinkInbox()
        inbox.submit("https://vrchat.com/home/user/usr_first")
        val first = inbox.pendingRequest.value!!

        inbox.submit("https://vrchat.com/home/world/wrld_second")
        val second = inbox.pendingRequest.value!!
        inbox.consume(first)

        assertEquals(second, inbox.pendingRequest.value)
        inbox.consume(second)
        assertNull(inbox.pendingRequest.value)
    }
}

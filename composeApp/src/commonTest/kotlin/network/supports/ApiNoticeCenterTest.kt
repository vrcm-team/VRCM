package io.github.vrcmteam.vrcm.network.supports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiNoticeCenterTest {
    @Test
    fun repeatedRateLimitsCoalesceUntilTheNoticeIsConsumed() {
        val center = ApiNoticeCenter()

        assertTrue(center.publish(ApiNotice.RateLimited))
        assertFalse(center.publish(ApiNotice.RateLimited))
        assertEquals(ApiNotice.RateLimited, center.activeNotice.value)

        assertTrue(center.consume(ApiNotice.RateLimited))
        assertTrue(center.publish(ApiNotice.RateLimited))
        assertEquals(ApiNotice.RateLimited, center.activeNotice.value)
    }
}

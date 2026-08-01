package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentUserSocketLocationTest {
    @Test
    fun instanceLocationSplitsIntoPresenceFields() {
        assertEquals(
            "wrld_test" to "instance~region(use)",
            socketLocationToPresenceParts("wrld_test:instance~region(use)"),
        )
    }

    @Test
    fun websiteAndOfflineLocationsRemainInstanceValues() {
        assertEquals("" to "", socketLocationToPresenceParts(""))
        assertEquals("" to "offline", socketLocationToPresenceParts("offline"))
    }
}

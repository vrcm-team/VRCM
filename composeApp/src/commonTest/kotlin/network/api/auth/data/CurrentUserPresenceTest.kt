package io.github.vrcmteam.vrcm.network.api.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentUserPresenceTest {
    @Test
    fun gamePresenceBuildsInstanceLocation() {
        assertEquals("wrld_test:instance", presenceLocation("wrld_test", "instance"))
    }

    @Test
    fun nonGamePresenceKeepsRawState() {
        assertEquals("", presenceLocation("", ""))
        assertEquals("offline", presenceLocation("", "offline"))
        assertEquals("traveling", presenceLocation("", "traveling"))
    }
}

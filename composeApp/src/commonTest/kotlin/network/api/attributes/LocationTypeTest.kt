package io.github.vrcmteam.vrcm.network.api.attributes

import kotlin.test.Test
import kotlin.test.assertEquals

class LocationTypeTest {
    @Test
    fun webLocationIsNotTreatedAsInstance() {
        assertEquals(LocationType.Web, LocationType.fromValue(LocationType.Web.value))
    }
}

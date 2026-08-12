package io.github.vrcmteam.vrcm.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VrchatStatusNotificationServiceTest {
    @Test
    fun firstObservationOnlyEstablishesBaseline() {
        assertNull(vrchatStatusTransition(null, "major", "Major outage"))
    }

    @Test
    fun operationalIncidentAndRecoveryProduceTransitionsOnce() {
        assertEquals(
            VrchatStatusTransition.Incident("major", "Major outage"),
            vrchatStatusTransition("none", " MAJOR ", " Major outage "),
        )
        assertNull(vrchatStatusTransition("major", "major", "Major outage"))
        assertEquals(
            VrchatStatusTransition.Restored,
            vrchatStatusTransition("major", "none", "All systems operational"),
        )
        assertNull(vrchatStatusTransition("none", "none", "All systems operational"))
    }

    @Test
    fun worseningIncidentProducesANewTransition() {
        assertEquals(
            VrchatStatusTransition.Incident("critical", "Critical outage"),
            vrchatStatusTransition("minor", "critical", "Critical outage"),
        )
    }
}

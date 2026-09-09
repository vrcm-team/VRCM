package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreditsBalanceStateTest {
    @Test
    fun forbiddenAndMissingEconomyAccountsAreUnavailable() {
        listOf(403, 404).forEach { status ->
            val error = VRCApiException(
                description = "Unavailable",
                code = status,
                bodyText = "",
            )

            assertEquals(CreditsBalanceState.Unavailable, creditsBalanceFailureState(error))
        }
    }

    @Test
    fun transientAndUnexpectedFailuresRemainRetryableErrors() {
        val failures = listOf(
            VRCApiException(description = "Server error", code = 500, bodyText = ""),
            IllegalStateException("Network unavailable"),
        )

        failures.forEach { error ->
            assertEquals(CreditsBalanceState.Error, creditsBalanceFailureState(error))
        }
    }

    @Test
    fun droppedRequestLeavesLoadingAndBecomesRetryable() {
        val stateMachine = CreditsBalanceStateMachine()
        val requestId = assertNotNull(stateMachine.tryStart())

        assertTrue(stateMachine.failDropped(requestId))
        assertEquals(CreditsBalanceState.Error, stateMachine.state.value)
        assertNotNull(stateMachine.tryStart())
    }

    @Test
    fun invalidatedSessionRejectsAnOldBalance() {
        val stateMachine = CreditsBalanceStateMachine()
        val requestId = assertNotNull(stateMachine.tryStart())

        stateMachine.invalidate()

        assertFalse(stateMachine.complete(requestId, balance = 2400))
        assertEquals(CreditsBalanceState.Unavailable, stateMachine.state.value)
    }
}

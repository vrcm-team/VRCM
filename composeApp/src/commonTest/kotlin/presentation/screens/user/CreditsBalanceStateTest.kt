package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

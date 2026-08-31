package io.github.vrcmteam.vrcm.presentation.screens.user

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoopPrivacyUiStateTest {
    @Test
    fun stateLoadsUntilCurrentUserMatchesAuthenticatedAccount() {
        val missingUser = resolveBoopPrivacyUiState(
            currentUserId = null,
            sessionUserId = "usr_self",
            isBoopingEnabled = null,
            updatingUserId = null,
        )
        val previousAccount = resolveBoopPrivacyUiState(
            currentUserId = "usr_previous",
            sessionUserId = "usr_self",
            isBoopingEnabled = false,
            updatingUserId = "usr_previous",
        )

        assertTrue(missingUser.isLoading)
        assertTrue(missingUser.isEnabled)
        assertFalse(missingUser.isUpdating)
        assertTrue(previousAccount.isLoading)
        assertTrue(previousAccount.isEnabled)
        assertFalse(previousAccount.isUpdating)
    }

    @Test
    fun matchingAccountShowsAuthoritativeValueAndItsOwnMutation() {
        val state = resolveBoopPrivacyUiState(
            currentUserId = "usr_self",
            sessionUserId = "usr_self",
            isBoopingEnabled = false,
            updatingUserId = "usr_self",
        )

        assertFalse(state.isLoading)
        assertFalse(state.isEnabled)
        assertTrue(state.isUpdating)
    }
}

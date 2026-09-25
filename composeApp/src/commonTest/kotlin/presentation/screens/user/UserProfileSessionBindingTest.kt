package io.github.vrcmteam.vrcm.presentation.screens.user

import io.github.vrcmteam.vrcm.core.shared.AccountSessionToken
import kotlin.test.Test
import kotlin.test.assertEquals

class UserProfileSessionBindingTest {
    @Test
    fun activeSessionWinsWhenPersistedAccountStillPointsToPreviousUser() {
        val activeToken = AccountSessionToken(userId = "usr_active", generation = 2)

        val binding = resolveProfileSessionBinding(
            activeSessionToken = activeToken,
            persistedOwnerUserId = "usr_stale",
        )

        assertEquals("usr_active", binding.ownerUserId)
        assertEquals(activeToken, binding.sessionToken)
    }

    @Test
    fun persistedAccountIsUsedUntilAuthenticationPublishesASession() {
        val binding = resolveProfileSessionBinding(
            activeSessionToken = null,
            persistedOwnerUserId = "usr_persisted",
        )

        assertEquals("usr_persisted", binding.ownerUserId)
        assertEquals(null, binding.sessionToken)
    }
}

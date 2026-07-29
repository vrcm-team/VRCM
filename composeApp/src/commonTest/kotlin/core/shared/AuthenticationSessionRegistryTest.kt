package io.github.vrcmteam.vrcm.core.shared

import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationSessionRegistryTest {
    @Test
    fun oldConnectionIsRejectedAcrossAccountSwitchAndReturn() {
        val registry = AuthenticationSessionRegistry()
        val firstAccountA = registry.authenticate(AccountDto(userId = "usr_a")).token

        registry.authenticate(AccountDto(userId = "usr_b"))
        val currentAccountA = registry.authenticate(AccountDto(userId = "usr_a")).token

        assertFalse(registry.isCurrent(firstAccountA))
        assertTrue(registry.isCurrent(currentAccountA))
    }

    @Test
    fun logoutInvalidatesTheCurrentConnectionImmediately() {
        val registry = AuthenticationSessionRegistry()
        val token = registry.authenticate(AccountDto(userId = "usr_a")).token

        registry.invalidate()

        assertFalse(registry.isCurrent(token))
    }

    @Test
    fun delayedOldEventCannotChangeNewAccountsMemoryOrCache() {
        val registry = AuthenticationSessionRegistry()
        val staleAccountA = registry.authenticate(AccountDto(userId = "usr_a")).token
        registry.authenticate(AccountDto(userId = "usr_b"))
        var memoryLocation = "wrld_b:1"
        val cachedLocations = mutableMapOf("usr_b" to "wrld_b:1")

        if (registry.isCurrent(staleAccountA)) {
            memoryLocation = "wrld_a:2"
            cachedLocations["usr_b"] = "wrld_a:2"
        }

        assertEquals("wrld_b:1", memoryLocation)
        assertEquals("wrld_b:1", cachedLocations.getValue("usr_b"))
    }
}

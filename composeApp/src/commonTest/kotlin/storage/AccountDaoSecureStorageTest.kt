package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.DaoKeys.Account.AUTH_KEY
import io.github.vrcmteam.vrcm.storage.DaoKeys.Account.PASSWORD_KEY
import io.github.vrcmteam.vrcm.storage.DaoKeys.Account.TWO_FACTOR_AUTH_KEY
import io.ktor.util.encodeBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AccountDaoSecureStorageTest {
    @Test
    fun secretsAreNotWrittenToRegularSettings() {
        val settings = MapSettings()
        val secureStorage = InMemorySecureStorage()
        val dao = AccountDao(settings, secureStorage)

        dao.saveAccountInfo(account())

        assertFalse(settings.keys.any { it.startsWith(PASSWORD_KEY) })
        assertFalse(settings.keys.any { it.startsWith(AUTH_KEY) })
        assertFalse(settings.keys.any { it.startsWith(TWO_FACTOR_AUTH_KEY) })
        assertEquals(account(), dao.currentAccountDtoOrNull())
    }

    @Test
    fun legacySecretsAreMigratedWithoutSigningTheUserOut() {
        val settings = MapSettings().apply {
            putString("${DaoKeys.Account.USERNAME_KEY}|usr_a", "alice")
            putBoolean("${DaoKeys.CURRENT_KEY}|usr_a", true)
            putString("$PASSWORD_KEY|usr_a", "password".encodeBase64())
            putString("$AUTH_KEY|usr_a", "auth-cookie")
            putString("$TWO_FACTOR_AUTH_KEY|usr_a", "2fa-cookie")
        }
        val dao = AccountDao(settings, InMemorySecureStorage())

        val restored = dao.currentAccountDtoOrNull()

        assertEquals("password", restored?.password)
        assertEquals("auth-cookie", restored?.authCookie)
        assertEquals("2fa-cookie", restored?.twoFactorAuthCookie)
        assertNull(settings.getStringOrNull("$PASSWORD_KEY|usr_a"))
        assertNull(settings.getStringOrNull("$AUTH_KEY|usr_a"))
        assertNull(settings.getStringOrNull("$TWO_FACTOR_AUTH_KEY|usr_a"))
    }

    private fun account() = AccountDto(
        userId = "usr_a",
        username = "alice",
        password = "password",
        current = true,
        authCookie = "auth-cookie",
        twoFactorAuthCookie = "2fa-cookie",
    )
}

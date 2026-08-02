package io.github.vrcmteam.vrcm.di.supports

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_COOKIE
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import org.koin.core.logger.EmptyLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentCookiesStorageTest {
    @Test
    fun vrChatCookiesAreOnlyReturnedToVrChatApiOverHttps() = runTest {
        val storage = PersistentCookiesStorage(EmptyLogger())
        storage.addCookie(Url("https://api.vrchat.cloud/api/1/auth/user"), Cookie(AUTH_COOKIE, "secret"))

        assertEquals(1, storage.get(Url("https://api.vrchat.cloud/api/1/auth/user")).size)
        assertTrue(storage.get(Url("https://api.github.com/repos/vrcm-team/VRCM/releases/latest")).isEmpty())
        assertTrue(storage.get(Url("http://api.vrchat.cloud/api/1/auth/user")).isEmpty())
    }

    @Test
    fun cookiesFromOtherHostsAreRejected() = runTest {
        val storage = PersistentCookiesStorage(EmptyLogger())

        storage.addCookie(Url("https://example.com"), Cookie(AUTH_COOKIE, "foreign"))

        assertTrue(storage.get(Url("https://api.vrchat.cloud/api/1/auth/user")).isEmpty())
    }
}

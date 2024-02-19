package io.github.kamo.vrcm.data.api

import androidx.compose.ui.util.fastFilterNotNull
import io.github.kamo.vrcm.data.dao.CookiesDao
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*

class PersistentCookiesStorage(
    private val cookiesDao: CookiesDao
) : CookiesStorage {

    override suspend fun get(requestUrl: Url): List<Cookie> = cookiesDao.allCookies
        .filter { it.value is String }
        .map { (key, value) ->
            println("requestUrl=$requestUrl")
            val cookie = parseServerSetCookieHeader(value as String)
            val currentKey = cookie.name + "@" + requestUrl.host
            if (currentKey == key) cookie else null
        }.fastFilterNotNull()


    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) =
        cookiesDao.saveCookies(cookie.name + "@" + requestUrl.host, "${cookie.name}=${cookie.value}")

    override fun close(): Unit = Unit

}
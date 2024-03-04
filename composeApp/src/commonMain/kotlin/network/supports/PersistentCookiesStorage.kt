package io.github.vrcmteam.vrcm.network.supports

import io.github.vrcmteam.vrcm.storage.CookiesDao
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import org.koin.core.logger.Logger

/**
 * Cookies storage that persists cookies in a database.
 * 数据库持久化Cookie存储
 */
class PersistentCookiesStorage(
    private val cookiesDao: CookiesDao,
    private val logger: Logger
) : CookiesStorage {

    override suspend fun get(requestUrl: Url): List<Cookie> {
        logger.debug("requestUrl=$requestUrl")
        return cookiesDao.allCookies
            .filter { it.value is String }
            .map { (key, value) ->
                val cookie = parseServerSetCookieHeader(value as String)
                val currentKey = cookie.name + "@" + requestUrl.host
                if (currentKey == key) cookie else null
            }.filterNotNull()
    }


    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) =
        cookiesDao.saveCookies(
            cookie.name + "@" + requestUrl.host,
            "${cookie.name}=${cookie.value}"
        )

    override fun close(): Unit = Unit

}
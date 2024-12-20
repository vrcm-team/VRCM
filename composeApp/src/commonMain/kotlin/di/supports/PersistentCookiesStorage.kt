package io.github.vrcmteam.vrcm.di.supports

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import org.koin.core.logger.Logger

/**
 * Cookies storage that persists cookies in a database.
 * 数据库持久化Cookie存储
 */
class PersistentCookiesStorage(
    private val logger: Logger
) : CookiesStorage {

    private val cookieCache: MutableMap<String, Cookie> = mutableMapOf()

    override suspend fun get(requestUrl: Url): List<Cookie> {
        return cookieCache.values.toList()
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        logger.info("requestUrl=$requestUrl")
        cookieCache[cookie.name] = cookie
    }

    fun addCookie(key: String, value: String?) =
        value?.takeIf { it.isNotEmpty() }
            ?.let { cookieCache[key] = parseServerSetCookieHeader("$key=$it") }


    override fun close() = cookieCache.clear()

    fun removeCookie(key: String) {
        cookieCache.remove(key)
    }

}
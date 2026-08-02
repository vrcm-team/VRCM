package io.github.vrcmteam.vrcm.di.supports

import io.ktor.client.plugins.cookies.*
import io.github.vrcmteam.vrcm.network.api.attributes.VRC_API_HOST
import io.ktor.http.*
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import org.koin.core.logger.Logger

/**
 * Cookies storage that persists cookies in a database.
 * 数据库持久化Cookie存储
 */
class PersistentCookiesStorage(
    private val logger: Logger
) : CookiesStorage {

    private val cookieCache: MutableMap<String, Cookie> = mutableMapOf()
    private val lock = SynchronizedObject()

    override suspend fun get(requestUrl: Url): List<Cookie> {
        if (requestUrl.protocol != URLProtocol.HTTPS || requestUrl.host != VRC_API_HOST) {
            return emptyList()
        }
        return synchronized(lock) { cookieCache.values.toList() }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (requestUrl.protocol != URLProtocol.HTTPS || requestUrl.host != VRC_API_HOST) return
        logger.info("requestUrl=$requestUrl")
        synchronized(lock) { cookieCache[cookie.name] = cookie }
    }

    fun addCookie(key: String, value: String?) =
        value?.takeIf { it.isNotEmpty() }
            ?.let { synchronized(lock) { cookieCache[key] = parseServerSetCookieHeader("$key=$it") } }

    fun cookieValue(key: String): String? = synchronized(lock) { cookieCache[key]?.value }


    override fun close() = synchronized(lock) { cookieCache.clear() }

    fun removeCookie(key: String) {
        synchronized(lock) { cookieCache.remove(key) }
    }

}

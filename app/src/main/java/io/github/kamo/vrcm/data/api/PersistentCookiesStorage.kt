package io.github.kamo.vrcm.data.api

import android.content.Context
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*

class PersistentCookiesStorage(context: Context) : CookiesStorage {

    private val preferences = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val cookies = mutableListOf<Cookie>()
        preferences.all.forEach { (key, value) ->
            if (value is String) {
                val cookie = parseServerSetCookieHeader(value)
                val currentKey = cookie.name + "@" + requestUrl.host
                println("get:$currentKey")
                if (currentKey == key) {
                    cookies.add(cookie)
                }
            }
        }
        return cookies
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        val editor = preferences.edit()
        val key = cookie.name + "@" + requestUrl.host
        println("addCookie:$key")
        editor.putString(key, "${cookie.name}=${cookie.value}")
        editor.apply()
    }

    override fun close(): Unit = Unit
}
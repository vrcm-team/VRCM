package io.github.vrcmteam.vrcm.data.dao

import coil3.PlatformContext

actual class CookiesDao actual constructor(context: PlatformContext) {
    private val cookies: MutableMap<String, Any> = mutableMapOf()
    actual val allCookies: Map<String, *>
        get() = cookies

    actual fun saveCookies(key: String, value: String) {
        cookies[key] = value
    }

    actual fun cookies(key: String): String? {
       return cookies[key] as? String
    }

    actual fun clearCookies() {
    }

    actual fun removeCookies(key: String) {
    }


}
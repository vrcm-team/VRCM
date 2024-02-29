package io.github.vrcmteam.vrcm.data.dao

import android.content.Context
import coil3.PlatformContext

actual class CookiesDao actual constructor(context: PlatformContext) {
    private val cookiesPreferences = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)

    actual val allCookies: Map<String, *>
        get() = cookiesPreferences.all

    actual fun saveCookies(key: String, value: String) = cookiesPreferences.edit().run {
        putString(key, value)
        apply()
    }

    actual fun cookies(key: String): String? = cookiesPreferences.getString(key, null)

    actual fun clearCookies() = cookiesPreferences.edit().run {
        clear()
        apply()
    }

    actual fun removeCookies(key: String) = cookiesPreferences.edit().run {
        remove(key)
        apply()
    }

}
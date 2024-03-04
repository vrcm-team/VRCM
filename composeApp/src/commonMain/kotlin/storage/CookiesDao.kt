package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set


class CookiesDao(
    private val cookiesSettings: Settings

)  {
     val allCookies: Map<String, *>
        get() = cookiesSettings.keys.associateWith { cookiesSettings.getStringOrNull(it) }

    fun saveCookies(key: String, value: String) =
        cookiesSettings.set(key, value)

     fun cookies(key: String): String? = cookiesSettings.getStringOrNull(key)

     fun clearCookies() = cookiesSettings.clear()

     fun removeCookies(key: String) = cookiesSettings.remove(key)
}

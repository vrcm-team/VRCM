package io.github.kamo.vrcm.data.dao;

import android.content.Context

class CookiesDao(context: Context) {
    private val cookiesPreferences = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)

    val allCookies: Map<String, *>
        get() = cookiesPreferences.all

    fun saveCookies(key: String, value: String) = cookiesPreferences.edit().run {
        putString(key, value)
        apply()
    }

    fun cookies(key: String): String? = cookiesPreferences.getString(key, null)

    fun clearCookies() = cookiesPreferences.edit().run {
        clear()
        apply()
    }

    fun removeCookies(key: String) = cookiesPreferences.edit().run {
        remove(key)
        apply()
    }

}

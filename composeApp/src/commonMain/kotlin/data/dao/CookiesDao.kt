package io.github.vrcmteam.vrcm.data.dao

import coil3.PlatformContext


expect class CookiesDao(context: PlatformContext)  {


    val allCookies: Map<String, *>

    fun saveCookies(key: String, value: String)

    fun cookies(key: String): String?

    fun clearCookies()

    fun removeCookies(key: String)

}

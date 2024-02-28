package io.github.vrcmteam.vrcm.data.dao

import coil3.PlatformContext

actual class CookiesDao actual constructor(context: PlatformContext) {
    actual val allCookies: Map<String, *>
        get() = TODO("Not yet implemented")

    actual fun saveCookies(key: String, value: String) {
    }

    actual fun cookies(key: String): String? {
        TODO("Not yet implemented")
    }

    actual fun clearCookies() {
    }

    actual fun removeCookies(key: String) {
    }


}
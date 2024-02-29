package io.github.vrcmteam.vrcm.data.dao

import coil3.PlatformContext

actual class AccountDao actual constructor(context: PlatformContext) {
    actual fun saveAccount(username: String, password: String) {
    }

    actual fun accountPair(): Pair<String, String> {
        return "123" to "123"
    }

    actual fun clearAccount() {
    }

}
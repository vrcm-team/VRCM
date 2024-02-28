package io.github.vrcmteam.vrcm.data.dao

import coil3.PlatformContext

actual class AccountDao actual constructor(context: PlatformContext) {
    actual fun saveAccount(username: String, password: String) {
    }

    actual fun accountPair(): Pair<String, String> {
        TODO("Not yet implemented")
    }

    actual fun clearAccount() {
    }

}
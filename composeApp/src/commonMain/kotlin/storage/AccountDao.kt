package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.util.*

class AccountDao(
    private val accountSettings: Settings
) {

    fun saveAccount(username: String, password: String) {
        accountSettings[DaoKeys.Account.USERNAME_KEY] = username
        accountSettings[DaoKeys.Account.PASSWORD_KEY] = password.encodeBase64()
    }

    fun accountPair(): Pair<String, String> =
        accountSettings.getString(DaoKeys.Account.USERNAME_KEY,"") to
                (accountSettings.getStringOrNull(DaoKeys.Account.PASSWORD_KEY)?.decodeBase64String()?:"")


    fun clearAccount() {
        accountSettings.clear()
    }

}

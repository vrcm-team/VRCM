package io.github.vrcmteam.vrcm.data.dao

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64


class AccountDao(
    private val accountSettings: Settings
) {

    fun saveAccount(username: String, password: String) {
        accountSettings[DaoKeys.USERNAME_KEY] = username
        accountSettings[DaoKeys.PASSWORD_KEY] = password.encodeBase64()
    }

    fun accountPair(): Pair<String, String> =
        accountSettings.getString(DaoKeys.USERNAME_KEY,"") to
                (accountSettings.getStringOrNull(DaoKeys.PASSWORD_KEY)?.decodeBase64String()?:"")


    fun clearAccount() {
        accountSettings.clear()
    }

}

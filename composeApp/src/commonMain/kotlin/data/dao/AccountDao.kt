package io.github.vrcmteam.vrcm.data.dao

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set


class AccountDao(
    private val accountSettings: Settings
) {

    fun saveAccount(username: String, password: String) {
        accountSettings[username] = password
    }

    fun accountPair(): Pair<String, String> =
        accountSettings.getString(DaoKeys.USERNAME_KEY,"")to
                accountSettings.getString(DaoKeys.PASSWORD_KEY,"")


    fun clearAccount() {
        accountSettings.clear()
    }

}

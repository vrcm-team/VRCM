package io.github.vrcmteam.vrcm.data.dao

import android.content.Context
import android.content.SharedPreferences
import coil3.PlatformContext

actual class AccountDao actual constructor(context: PlatformContext) {
    private val userSharedPreferences: SharedPreferences = context.getSharedPreferences("user", Context.MODE_PRIVATE)

     actual fun saveAccount(username: String, password: String) = userSharedPreferences.edit().run {
        putString(DaoKeys.USERNAME_KEY, username)
        putString(DaoKeys.PASSWORD_KEY, password)
        apply()
    }

     actual fun accountPair(): Pair<String, String> = userSharedPreferences.run {
        val username = getString(DaoKeys.USERNAME_KEY, null) ?: ""
        val password = getString(DaoKeys.PASSWORD_KEY, null) ?: ""
        return username to password
    }

    actual fun clearAccount() = userSharedPreferences.edit().run {
        remove(DaoKeys.USERNAME_KEY)
        remove(DaoKeys.PASSWORD_KEY)
        apply()
    }

}
package io.github.kamo.vrcm.data.dao

import android.content.Context
import android.content.SharedPreferences

class AccountDao(context: Context) {
    companion object {
        private const val USERNAME_KEY = "username"
        private const val PASSWORD_KEY = "password"
    }

    private val userSharedPreferences: SharedPreferences = context.getSharedPreferences("user", Context.MODE_PRIVATE)

    fun saveAccount(username: String, password: String) = userSharedPreferences.edit().run {
        putString(USERNAME_KEY, username)
        putString(PASSWORD_KEY, password)
        apply()
    }

    fun accountPair(): Pair<String, String> = userSharedPreferences.run {
        val username = getString(USERNAME_KEY, null) ?: ""
        val password = getString(PASSWORD_KEY, null) ?: ""
        return username to password
    }

    // 定义一个函数，用于清除本地的用户名和密码
    fun clearAccount() = userSharedPreferences.edit().run {
        remove(USERNAME_KEY)
        remove(PASSWORD_KEY)
        apply()
    }

}
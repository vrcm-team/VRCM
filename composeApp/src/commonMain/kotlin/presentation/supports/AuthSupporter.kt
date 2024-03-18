package io.github.vrcmteam.vrcm.presentation.supports

import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.network.api.attributes.AuthType
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.storage.AccountDao

class AuthSupporter(
    private val authApi: AuthApi,
    private val accountDao: AccountDao,
) {
    fun accountPair(): Pair<String, String> = accountDao.accountPair()

    fun accountPairOrNull(): Pair<String, String>? = accountDao.accountPairOrNull()
    suspend fun isAuthed()  = authApi.isAuthed()

    suspend fun currentUser() = authApi.currentUser()


    suspend fun verify(code: String, authCardPage: AuthCardPage): Boolean {
        val authType = when (authCardPage) {
            AuthCardPage.EmailCode -> AuthType.Email
            AuthCardPage.TFACode -> AuthType.TFA
            else -> error("not supported")
        }
       return authApi.verify(code, authType)
    }

    suspend fun login(username: String, password: String): AuthState =
        authApi.login(username, password).also {
            if (it == AuthState.Authed) {
                accountDao.saveAccount(username, password)
            }
        }

    private suspend fun <T> Result<T>.recoverLogin(callback: suspend () -> Result<T>): Result<T> {
        return when (exceptionOrNull()) {
            null -> this
            else -> {
                val (username, password) = accountDao.accountPairOrNull() ?: return this
                authApi.login(username, password)
                    .takeIf { it == AuthState.Authed }
                    ?.let { callback() } ?: this
            }
        }
    }

    /**
     * 如果验证过期了尝试登陆后再请求一次
     */
    suspend fun <T> tryAuth( callback: suspend () -> Result<T>): Result<T> =
        callback().recoverLogin(callback)

}

package io.github.vrcmteam.vrcm.presentation.supports

import io.github.vrcmteam.vrcm.core.subscribe.AuthCentre
import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.network.api.attributes.AuthType
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.storage.AccountDao

/**
 * 负责辅助登录验证的类
 * 主要作用是统一验证失效时的重试逻辑
 * @author kamosama
 */
class AuthSupporter(
    private val authApi: AuthApi,
    private val accountDao: AccountDao,
) {
    fun accountPair(): Pair<String, String> = accountDao.accountPair()

    fun accountPairOrNull(): Pair<String, String>? = accountDao.accountPairOrNull()

    suspend fun isAuthed():Boolean = authApi.isAuthed().also { if (it) AuthCentre.Publisher.sendAuthed(null, null) }
    suspend fun currentUser() = authApi.currentUser()


    suspend fun verify(code: String, authCardPage: AuthCardPage): Boolean {
        val authType = when (authCardPage) {
            AuthCardPage.EmailCode -> AuthType.Email
            AuthCardPage.TFACode -> AuthType.TFA
            else -> error("not supported")
        }
       return authApi.verify(code, authType).also { if (it) AuthCentre.Publisher.sendAuthed(null, null) }
    }

    suspend fun login(username: String, password: String): AuthState =
        authApi.login(username, password).also {
            if (it != AuthState.Authed) return@also
            if (username.isNotBlank() && password.isNotBlank()) {
                accountDao.saveAccount(username, password)
            }
            AuthCentre.Publisher.sendAuthed(null, null)
        }

    /**
     * 如果是失败的结果则会判断是否是验证失效了
     * 如果是则尝试重新登陆
     */
    private suspend fun <T> Result<T>.recoverLogin(callback: suspend () -> Result<T>): Result<T> {
        return when (val exception = exceptionOrNull()) {
            null -> this
            else -> if (exception is VRCApiException && doReTryAuth()) {
                callback()
            } else {
                this
            }
        }
    }

    suspend fun doReTryAuth():Boolean {
        val (username, password) = accountDao.accountPairOrNull() ?: return false
        return authApi.login(username, password)
            .takeIf { it == AuthState.Authed }
            ?.let { true } ?: false
    }

    /**
     * 如果验证过期了尝试登陆后再请求一次
     */
    suspend fun <T> reTryAuth(callback: suspend () -> Result<T>): Result<T> =
        callback().recoverLogin(callback)

    suspend fun <T> reTryAuthCatching(callback: suspend () -> T): Result<T> =
       reTryAuth {
           runCatching { callback() }
       }

}

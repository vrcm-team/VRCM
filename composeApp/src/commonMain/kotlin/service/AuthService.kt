package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.supports.PersistentCookiesStorage
import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_COOKIE
import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.network.api.attributes.AuthType
import io.github.vrcmteam.vrcm.network.api.attributes.TWO_FACTOR_AUTH_COOKIE
import io.github.vrcmteam.vrcm.network.api.auth.AuthApi
import io.github.vrcmteam.vrcm.network.api.auth.data.CurrentUserData
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.github.vrcmteam.vrcm.storage.AccountDao
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 负责辅助登录验证的类
 * 主要作用是统一验证失效时的重试逻辑
 * @author kamosama
 */
class AuthService(
    private val authApi: AuthApi,
    private val accountDao: AccountDao,
    private val cookiesStorage: PersistentCookiesStorage
) {
    private var scope = CoroutineScope(Job())

    private var currentUser: CurrentUserData?  = null

    private var currentAccountDto : AccountDto? = null
    fun accountDto(): AccountDto = currentAccountDto ?: accountDao.currentAccountDto()

    fun accountDtoOrNull(): AccountDto? = accountDao.currentAccountDtoOrNull()

    suspend fun isAuthed():Boolean = authApi.isAuthed().also { if (it) emitAuthed() }

    suspend fun currentUser(isRefresh: Boolean = false) = if (currentUser != null && !isRefresh) {
        currentUser!!
    } else {
        authApi.currentUser().also {
            currentUser = it
        }
    }

    init {
        scope.launch {
            SharedFlowCentre.authed.collect { accountDto ->
                currentAccountDto = accountDto
                accountDao.saveAccountInfo(accountDto)
            }
        }
    }

    suspend fun verify(
        password: String,
        verifyCode: String,
        authCardPage: AuthCardPage
    ): Result<Unit> {
        val authType = when (authCardPage) {
            AuthCardPage.EmailCode -> AuthType.Email
            AuthCardPage.TFACode -> AuthType.TFA
            AuthCardPage.TTFACode -> AuthType.TTFA
            else -> error("not supported")
        }
       return authApi.verify(verifyCode, authType)
           .also { if (it.isSuccess) emitAuthed(password) }
    }

    private suspend fun emitAuthed(password: String? = null) {
        authApi.userRes().let {
            if (!it.status.isSuccess()){
                SharedFlowCentre.toastText.emit(ToastText.Error("emitAuthed: ${it.status} ${it.bodyAsText()} "))
                return
            }
            var authCookie: String? = null
            var twoFactorAuthCookie: String? = null
            it.request.headers[HttpHeaders.Cookie]?.let { cookieHeader ->
                parseClientCookiesHeader(cookieHeader)
                    .forEach {(name, encodedValue) ->
                        when(name){
                            AUTH_COOKIE -> authCookie = encodedValue
                            TWO_FACTOR_AUTH_COOKIE -> twoFactorAuthCookie = encodedValue
                        }
                    }
            }
            val userData = it.body<CurrentUserData>()
            val accountDto = AccountDto(
                userId = userData.id,
                username = userData.username,
                password = password,
                iconUrl = userData.iconUrl,
                authCookie = authCookie,
                twoFactorAuthCookie = twoFactorAuthCookie
            )
            SharedFlowCentre.authed.emit(accountDto)
        }

    }

    suspend fun login(username: String, password: String): AuthState {
        val info = accountDao.accountDtoByUserName(username)
            info?.let {
                cookiesStorage.addCookie(AUTH_COOKIE, it.authCookie)
                cookiesStorage.addCookie(TWO_FACTOR_AUTH_COOKIE, it.twoFactorAuthCookie)
            }
        return authApi.login(username, password).also {
            if (it is AuthState.Authed) emitAuthed(password)
        }
    }


    /**
     * 如果是失败的结果则会判断是否是验证失效了
     * 如果是则尝试重新登陆
     */
    private suspend fun <T> Result<T>.recoverLogin(callback: suspend () -> Result<T>): Result<T> {
        return when (val exception = exceptionOrNull()) {
            null -> this
            else -> if (exception is VRCApiException && exception.code == HttpStatusCode.Unauthorized.value && doReTryAuth()) {
                callback()
            } else {
                this
            }
        }
    }

    suspend fun doReTryAuth():Boolean {
        val accountInfo = accountDao.currentAccountDtoOrNull() ?: return false
        return authApi.login(accountInfo.username, accountInfo.password!!) is AuthState.Authed
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

    fun logout() {
        cookiesStorage.removeCookie(AUTH_COOKIE)
        accountDao.logout(currentUser?.id ?: accountDto().userId)
        scope.launch {
            SharedFlowCentre.logout.emit(Unit)
        }
    }

}
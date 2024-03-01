package io.github.vrcmteam.vrcm.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.data.api.AuthType
import io.github.vrcmteam.vrcm.data.api.auth.AuthApi
import io.github.vrcmteam.vrcm.data.api.auth.AuthState
import io.github.vrcmteam.vrcm.data.dao.AccountDao
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.core.logger.Logger


enum class AuthCardPage {
    Loading,
    Login,
    EmailCode,
    TFACode,
    Authed,
    TTFACode
}

class AuthScreenModel(
    private val authAPI: AuthApi,
    private val accountDao: AccountDao,
    private val logger: Logger
) : ScreenModel {

    private val _uiState = mutableStateOf(accountDao.accountPair().run {
        AuthUIState(username = first, password = second)
    })

    private var _currentVerifyJob: Job? = null

    val uiState: AuthUIState by _uiState

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onVerifyCodeChange(verifyCode: String) {
        _uiState.value = _uiState.value.copy(verifyCode = verifyCode)
    }

    fun onLoadingChange(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(btnIsLoading = isLoading)
    }

    fun onErrorMessageChange(errorMsg: String) {
        if (_uiState.value.btnIsLoading) {
            _uiState.value = _uiState.value.copy(errorMsg = errorMsg, btnIsLoading = false)
        } else {
            _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
        }
    }

    fun onCardStateChange(cardState: AuthCardPage) {
        if (cardState == AuthCardPage.Authed) {
            accountDao.saveAccount(uiState.username, uiState.password)
        }
        _uiState.value = when (cardState) {
            AuthCardPage.EmailCode, AuthCardPage.TFACode -> _uiState.value.copy(
                cardState = cardState,
                verifyCode = "",
                btnIsLoading = false,
            )

            else -> _uiState.value.copy(
                cardState = cardState,
                btnIsLoading = false,
            )
        }
    }

    fun cancelJob() {
        _currentVerifyJob?.cancel()
        _currentVerifyJob = null
    }

    fun tryAuth(){
        screenModelScope.launch{
            val cardState = if (awaitAuth()) AuthCardPage.Authed else AuthCardPage.Login
            onCardStateChange(cardState)
        }
    }

    private suspend fun awaitAuth(): Boolean = screenModelScope.async(Dispatchers.IO) {
        runCatching { authAPI.isAuthed() }.onAuthFailure().getOrNull()
    }.await() == true


    fun login() {
        val password = _uiState.value.password.trim()
        val username = _uiState.value.username.trim()
        if (password.isEmpty() || username.isEmpty() || _uiState.value.btnIsLoading) {
            if (password.isEmpty() || username.isEmpty()) {
                onErrorMessageChange("Username or Password is empty")
            }
            return
        }
        onLoadingChange(true)
        screenModelScope.launch(context = Dispatchers.Default) {
            doLogin(username, password)
        }
    }


    fun verify() {
        val verifyCode = _uiState.value.verifyCode
        if (verifyCode.isEmpty() || verifyCode.length != 6 || _uiState.value.btnIsLoading) return
        onLoadingChange(true)
        _currentVerifyJob = screenModelScope.launch(context = Dispatchers.Default) {

            val result = async(context = Dispatchers.IO) {
                val authType = when (_uiState.value.cardState) {
                    AuthCardPage.EmailCode -> AuthType.Email
                    AuthCardPage.TFACode -> AuthType.TFA
                    else -> error("not supported")
                }
                authAPI.verify(verifyCode, authType)
            }.await()
            if (result) {
                onCardStateChange(AuthCardPage.Authed)
            } else {
                onErrorMessageChange("Invalid Code")
            }
        }
    }

    private suspend fun doLogin(username: String, password: String) {
        val runCatching = runCatching { authAPI.login(username, password) }
        val authStateResult = runCatching
            .onAuthFailure()
        runCatching.getOrThrow()
        if (authStateResult.isFailure) return
        val authState = authStateResult.getOrNull()!!

        if (AuthState.Unauthorized == authState) {
            onErrorMessageChange("Username or Password is incorrect")
            return
        }
        val authCardPage = when (authState) {
            AuthState.Authed -> AuthCardPage.Authed

            AuthState.NeedEmailCode -> AuthCardPage.EmailCode

            AuthState.NeedTFA -> AuthCardPage.TFACode

            AuthState.NeedTTFA -> AuthCardPage.TTFACode

            else -> error("not supported")

        }
        onCardStateChange(authCardPage)
    }
    private fun Result<*>.onAuthFailure() =
        onFailure {
            logger.error("AuthScreen Failed : ${it.message}")
            val message = if(it is UnresolvedAddressException)  "Unable to connect to the network" else it.message
            onErrorMessageChange("Failed to Auth: $message")
            onCardStateChange(AuthCardPage.Login)
        }
}

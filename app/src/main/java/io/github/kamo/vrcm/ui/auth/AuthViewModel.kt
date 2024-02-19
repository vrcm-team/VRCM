package io.github.kamo.vrcm.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.auth.AuthState.*
import io.github.kamo.vrcm.data.api.auth.AuthType
import io.github.kamo.vrcm.data.dao.AccountDao
import kotlinx.coroutines.*


enum class AuthCardPage {
    Loading,
    Login,
    EmailCode,
    TFACode,
    Authed,
}

class AuthViewModel(
    private val authAPI: AuthAPI,
    private val accountDao: AccountDao,
) : ViewModel() {

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
        _uiState.value = _uiState.value.copy(isLoading = isLoading)
    }

    fun onErrorMessageChange(errorMsg: String) {
        if (_uiState.value.isLoading) {
            _uiState.value = _uiState.value.copy(errorMsg = errorMsg, isLoading = false)
        } else {
            _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
        }
    }

    fun onCardStateChange(cardState: AuthCardPage) {

        // 主界面跳回登录验证界面需要手动操作下,为了正确的显示出现和消失动画
        if (cardState == AuthCardPage.Loading && _uiState.value.cardState == AuthCardPage.Authed) {

            _uiState.value = _uiState.value.copy(
                cardState = AuthCardPage.Loading,
                isLoading = false,
            )
            viewModelScope.launch {
                delay(2000)
                onCardStateChange(AuthCardPage.Login)
            }
            return
        }
        if (cardState == AuthCardPage.Authed) {
            accountDao.saveAccount(uiState.username, uiState.password)
        }

        _uiState.value = when (cardState) {
            AuthCardPage.EmailCode, AuthCardPage.TFACode -> _uiState.value.copy(
                cardState = cardState,
                verifyCode = "",
                isLoading = false,
            )

            else -> _uiState.value.copy(
                cardState = cardState,
                isLoading = false,
            )
        }
    }

    fun cancelJob() {
        _currentVerifyJob?.cancel()
        _currentVerifyJob = null
    }

    suspend fun awaitAuth(): Boolean = viewModelScope.async(Dispatchers.IO) { authAPI.isAuthed() }.await() == true


    fun login() {
        val password = _uiState.value.password.trim()
        val username = _uiState.value.username.trim()
        if (password.isEmpty() || username.isEmpty() || _uiState.value.isLoading) {
            if (password.isEmpty() || username.isEmpty()) {
                onErrorMessageChange("Username or Password is empty")
            }
            return
        }
        onLoadingChange(true)
        viewModelScope.launch(context = Dispatchers.Default) {
            doLogin(username, password)
        }
    }


    fun verify() {
        val verifyCode = _uiState.value.verifyCode
        if (verifyCode.isEmpty() || verifyCode.length != 6 || _uiState.value.isLoading) return
        onLoadingChange(true)
        _currentVerifyJob = viewModelScope.launch(context = Dispatchers.Default) {

            val result = async(context = Dispatchers.IO) {
                val authType = when (_uiState.value.cardState) {
                    AuthCardPage.EmailCode -> AuthType.Email
                    AuthCardPage.TFACode -> AuthType.TFA
                    else -> error("not supported")
                }
                authAPI.verify(verifyCode, authType)
            }.await()
            if (result) {
                doLogin(_uiState.value.username, _uiState.value.password)
            } else {
                onErrorMessageChange("Invalid Code")
            }
        }
    }

    private suspend fun doLogin(username: String, password: String) {
        val authState = authAPI.login(username, password)
        if (Unauthorized == authState) {
            onErrorMessageChange("Username or Password is incorrect")
            return
        }
        val authCardPage = when (authState) {
            Authed -> AuthCardPage.Authed

            NeedTFA -> AuthCardPage.TFACode

            NeedEmailCode -> AuthCardPage.EmailCode

            Unauthorized -> error("not supported")

        }
        onCardStateChange(authCardPage)
    }


}

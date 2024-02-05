package io.github.kamo.vrcm.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.auth.AuthState.*
import io.github.kamo.vrcm.data.api.auth.AuthType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch


enum class AuthCardState {
    Login,
    EmailCode,
    TFACode,
    Authed,
}

class AuthViewModel(private val authAPI: AuthAPI) : ViewModel() {
    private val _uiState = mutableStateOf(AuthUIState())
    val uiState: AuthUIState by _uiState
    private var _currentJob: Job? = null
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

    fun onCardStateChange(cardState: AuthCardState) {
        _uiState.value = when (cardState) {

            AuthCardState.EmailCode, AuthCardState.TFACode -> _uiState.value.copy(
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
        _currentJob?.cancel()
        _currentJob = null
    }


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
        _currentJob = viewModelScope.launch(context = Dispatchers.Default) {
            doLogin(username, password)
        }
    }


    fun verify() {
        val verifyCode = _uiState.value.verifyCode
        if (verifyCode.isEmpty() || verifyCode.length != 6 || _uiState.value.isLoading) return
        onLoadingChange(true)
        _currentJob = viewModelScope.launch(context = Dispatchers.Default) {

            val result = async (context = Dispatchers.IO) {
                val authType = when (_uiState.value.cardState) {
                    AuthCardState.EmailCode -> AuthType.Email
                    AuthCardState.TFACode -> AuthType.TFA
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
        val authCardState = when (authState) {
            Authed -> AuthCardState.Authed

            NeedTFA -> AuthCardState.TFACode

            NeedEmailCode -> AuthCardState.EmailCode

            Unauthorized -> error("not supported")

        }
        onCardStateChange(authCardState)
    }

    fun reset() {
        _uiState.value = AuthUIState()
    }
}
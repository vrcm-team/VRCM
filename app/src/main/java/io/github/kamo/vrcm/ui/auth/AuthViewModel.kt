package io.github.kamo.vrcm.ui.auth

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

enum class AuthCardState {
    Login,
    EmailCode,
    TFACode,
    NONE,
}

class AuthViewModel : ViewModel() {
    private val _uiState = mutableStateOf(AuthUIState())
    val uiState: State<AuthUIState> = _uiState
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
        _uiState.value = _uiState.value.copy(errorMsg = errorMsg)
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


    fun login(doNavigate: () -> Unit) {
        val password = _uiState.value.password.trim()
        val username = _uiState.value.username.trim()
        if (password.isEmpty() || username.isEmpty() || _uiState.value.isLoading ) {
            if (password.isEmpty() || username.isEmpty()){
                onErrorMessageChange("Username or Password is empty")
            }
            return
        }
        onLoadingChange(true)
        _currentJob = viewModelScope.launch(context = Dispatchers.Default) {
//            val authHeader = Configuration.getDefaultApiClient().getAuthentication("authHeader") as HttpBasicAuth
//            authHeader.username = username
//            authHeader.password = password
//            runCatching {
//                println(authApiClient.currentUserWithHttpInfo)
//            }.recoverCatching {
//                println(it)
//                if (it.message?.contains("2FA") == true) {
//                    onCardStateChange(AuthCardState.TFACode)
//                } else if (it.message?.contains("email") == true) {
//                    onCardStateChange(AuthCardState.EmailCode)
//                }
//            }
            val result = runBlocking(context = Dispatchers.IO) {
                delay(1000)
            }
            launch(context = Dispatchers.Main) {
                onCardStateChange(AuthCardState.EmailCode)
//               doNavigate()
            }

        }
    }

    fun verify(doNavigate: () -> Unit) {
        val verifyCode = _uiState.value.verifyCode
        if (verifyCode.isEmpty() || verifyCode.length != 6 || _uiState.value.isLoading) return
        onLoadingChange(true)
        _currentJob = viewModelScope.launch(context = Dispatchers.Default) {
//            val reps =  try {
//                when (_uiState.value.cardState) {
//                    AuthCardState.EmailCode -> {
//                        authApiClient.verify2FAEmailCodeWithHttpInfo(TwoFactorEmailCode().code(_uiState.value.verifyCode))
//                    }
//
//                    AuthCardState.TFACode -> {
//                        authApiClient.verify2FAWithHttpInfo(TwoFactorAuthCode().code(_uiState.value.verifyCode))
//                    }
//
//                    else -> {
//                        return@launch
//                    }
//                }
//            }catch (e: ApiException) {
//                Log.e("AuthViewModel", e.message.toString())
//                return@launch
//            }

//            if (reps != null && reps.statusCode == 200 && reps.data.toString().contains("verified: true")) {
//                println(reps.data)
//                println(authApiClient.currentUserWithHttpInfo)
//                _channel.send(true)
//            }
            val result = runBlocking(context = Dispatchers.IO) {
                delay(1500)
            }
//            onCardStateChange(AuthCardState.Login)
            launch(context = Dispatchers.Main) {
                doNavigate()
            }

        }
    }

    fun reset() {
        _uiState.value = AuthUIState()
    }
}
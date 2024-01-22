package io.github.kamo.vrcm.ui.auth

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.vrchatapi.ApiClient
import io.github.vrchatapi.ApiException
import io.github.vrchatapi.ApiResponse
import io.github.vrchatapi.Configuration
import io.github.vrchatapi.api.AuthenticationApi
import io.github.vrchatapi.auth.ApiKeyAuth
import io.github.vrchatapi.auth.HttpBasicAuth
import io.github.vrchatapi.model.TwoFactorAuthCode
import io.github.vrchatapi.model.TwoFactorEmailCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class AuthCardState {
    Login,
    EmailCode,
    TFACode,
    Loading
}

class AuthViewModel : ViewModel() {
    private var _uiState = mutableStateOf(AuthUIState())
    val uiState: State<AuthUIState> = _uiState

    private val _channel = Channel<Boolean>()
    val channel = _channel.receiveAsFlow()

    private val authApiClient = AuthenticationApi()

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onVerifyCodeChange(verifyCode: String) {
        _uiState.value = _uiState.value.copy(verifyCode = verifyCode)
    }

    fun onCardStateChange(cardState: AuthCardState) {
        _uiState.value = when (cardState) {

            AuthCardState.EmailCode, AuthCardState.TFACode -> _uiState.value.copy(
                cardState = cardState,
                verifyCode = ""
            )

            else -> _uiState.value.copy(cardState = cardState)
        }
    }

    fun login() {
        val password = _uiState.value.password.trim()
        val username = _uiState.value.username.trim()
        if (password.isEmpty() || username.isEmpty()) return
        onCardStateChange(AuthCardState.Loading)
        viewModelScope.launch(context = Dispatchers.IO) {
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
            delay(1000)
            onCardStateChange(AuthCardState.EmailCode)
        }
    }

    fun verify() {
        val verifyCode = _uiState.value.verifyCode
        if (verifyCode.isEmpty() || verifyCode.length != 6) return
        onCardStateChange(AuthCardState.Loading)
        viewModelScope.launch(context = Dispatchers.IO) {
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
            delay(1500)
            onCardStateChange(AuthCardState.Login)

        }
    }
}
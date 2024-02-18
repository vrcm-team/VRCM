package io.github.kamo.vrcm.ui.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kamo.vrcm.data.api.auth.AuthAPI
import io.github.kamo.vrcm.data.api.auth.AuthState.*
import io.github.kamo.vrcm.data.api.auth.AuthType
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
    context: Context
) : ViewModel() {
    companion object {
        // 定义一个常量，用于存储用户名和密码的键
        private const val USERNAME_KEY = "username"
        private const val PASSWORD_KEY = "password"
    }

    // 定义一个函数，用于获取 shared preferences 的实例
    private val userSharedPreferences: SharedPreferences = context.getSharedPreferences("user", Context.MODE_PRIVATE)

    private val _uiState = mutableStateOf(accountPair().run { AuthUIState(username = first, password = second) })

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
            saveAccount(uiState.username, uiState.password)
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

    suspend fun awaitAuth(): Boolean? {
        return viewModelScope.async(Dispatchers.IO) {
            runCatching { authAPI.isAuthed() }.getOrNull()
        }.await()
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


    // 定义一个函数，用于保存用户名和密码到本地
    fun saveAccount(username: String, password: String) = userSharedPreferences.edit().run {
        putString(USERNAME_KEY, username)
        putString(PASSWORD_KEY, password)
        apply()
    }

    // 定义一个函数，用于从本地获取用户名和密码
    private fun accountPair(): Pair<String, String> = userSharedPreferences.run {
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
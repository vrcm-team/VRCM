package io.github.vrcmteam.vrcm.presentation.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.AuthState
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthCardPage
import io.github.vrcmteam.vrcm.presentation.screens.auth.data.AuthUIState
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.VersionService
import io.github.vrcmteam.vrcm.service.data.AccountDto
import kotlinx.coroutines.*
import org.koin.core.logger.Logger
import presentation.screens.auth.data.VersionVo


class AuthScreenModel(
    private val authService: AuthService,
    private val versionService: VersionService,
    private val logger: Logger,
) : ScreenModel {

    private val _uiState = mutableStateOf(authService.accountDto().run {
        AuthUIState(
            userId = userId,
            iconUrl = iconUrl,
            username = username,
            password = password.orEmpty()
        )
    })

    private var _currentVerifyJob: Job? = null

    fun accountDtoList():List<AccountDto> = authService.accountDtoList()

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

    fun onAccountChange(accountDto: AccountDto) {
        _uiState.value = _uiState.value.copy(
            userId = accountDto.userId,
            iconUrl = accountDto.iconUrl,
            username = accountDto.username,
            password = accountDto.password.orEmpty(),
        )
    }

    fun onErrorMessageChange(errorMsg: String) {
        if (_uiState.value.btnIsLoading) {
            _uiState.value = _uiState.value.copy(btnIsLoading = false)
        }
        logger.error(errorMsg)
        screenModelScope.launch {
            SharedFlowCentre.toastText.emit(ToastText.Error(errorMsg))
        }
    }

    fun onCardStateChange(cardState: AuthCardPage) {
        _uiState.value = when (cardState) {
            AuthCardPage.Login -> {
                authService.logout()
                _uiState.value.copy(
                    cardState = cardState,
                    verifyCode = "",
                    btnIsLoading = false,
                )
            }

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

    fun tryAuth() {
        screenModelScope.launch {
            val cardState = if (awaitAuth()) AuthCardPage.Authed else AuthCardPage.Login
            onCardStateChange(cardState)
        }
    }

    private suspend fun awaitAuth(): Boolean = screenModelScope.async(Dispatchers.IO) {
        authService.reTryAuth {
            runCatching { authService.isAuthed() }
        }.onAuthFailure()
            .getOrNull()
    }.await() == true

    fun tryCheckVersion(onCheckVersion:(VersionVo) -> Unit)=
         screenModelScope.launch(Dispatchers.IO) {
            versionService.checkVersion(true)
                .onAuthFailure()
                .map { VersionVo(it.tagName, it.htmlUrl, it.body, it.hasNewVersion) }
                .getOrElse { VersionVo() }
                .also(onCheckVersion)
        }


    fun rememberVersion(version: String?) = screenModelScope.launch(Dispatchers.IO) {
        versionService.rememberVersion(version)
    }

    fun login() {
        if (_uiState.value.btnIsLoading) return
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password.trim()
        if (password.isEmpty() || username.isEmpty()) {
            onErrorMessageChange("Username or Password is empty")
        } else {
            onLoadingChange(true)
            screenModelScope.launch(context = Dispatchers.Default) {
                doLogin(username, password)
            }
        }
    }


    fun verify() {
        val verifyCode = _uiState.value.verifyCode
        val password = _uiState.value.password.trim()
        if (verifyCode.isEmpty() || verifyCode.length != 6 || _uiState.value.btnIsLoading) return
        onLoadingChange(true)
        _currentVerifyJob = screenModelScope.launch(context = Dispatchers.Default) {
            async(context = Dispatchers.IO) {
                authService.verify( password, verifyCode, _uiState.value.cardState)
            }.await()
                .onSuccess {
                    onCardStateChange(AuthCardPage.Authed)
                }.onAuthFailure()
        }
    }

    private suspend fun doLogin(username: String, password: String) {
        val runCatching = runCatching { authService.login(username, password) }.onAuthFailure()
        if (runCatching.isFailure) return
        val authState = runCatching.getOrNull()!!

        if (authState is AuthState.Unauthorized) {
            onErrorMessageChange(authState.message)
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

    private inline fun <T> Result<T>.onAuthFailure() =
        onApiFailure("Auth") {
            logger.error(it)
            onErrorMessageChange(it)
        }

    fun removeAccount(userId: String) = authService.removeAccount(userId)

}

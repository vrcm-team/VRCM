package io.github.kamo.vrcm.ui.auth

data class AuthUIState(
    val username: String = "",
    val password: String = "",
    val verifyCode: String = "",
    val errorMsg: String = "",
    val isLoading: Boolean = false,
    val cardState: AuthCardState = AuthCardState.Login,
)


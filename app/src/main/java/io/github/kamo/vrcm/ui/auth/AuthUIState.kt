package io.github.kamo.vrcm.ui.auth

data class AuthUIState(
    val username: String = "",
    val password: String = "",
    val verifyCode: String = "",
    val errorMsg: String = "",
    val cardState: AuthCardState = AuthCardState.Login,
)


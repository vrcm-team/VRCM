package io.github.vrcmteam.vrcm.screens.auth

data class AuthUIState(
    val username: String = "",
    val password: String = "",
    val verifyCode: String = "",
    val errorMsg: String = "",
    val btnIsLoading: Boolean = false,
    val cardState: AuthCardPage = AuthCardPage.Loading,
)


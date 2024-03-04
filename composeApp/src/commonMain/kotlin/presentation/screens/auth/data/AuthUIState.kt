package io.github.vrcmteam.vrcm.presentation.screens.auth.data

data class AuthUIState(
    val username: String = "",
    val password: String = "",
    val verifyCode: String = "",
    val errorMsg: String = "",
    val btnIsLoading: Boolean = false,
    val cardState: AuthCardPage = AuthCardPage.Loading,
)
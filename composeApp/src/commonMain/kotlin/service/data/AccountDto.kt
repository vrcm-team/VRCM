package io.github.vrcmteam.vrcm.service.data


data class AccountDto(
    val userId: String = "",
    val username: String = "",
    val password: String? = null,
    val iconUrl: String? = null,
    val current: Boolean = false,
    val authCookie: String? = null,
    val twoFactorAuthCookie: String? = null,
)
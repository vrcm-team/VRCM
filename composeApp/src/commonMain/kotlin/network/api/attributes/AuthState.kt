package io.github.vrcmteam.vrcm.network.api.attributes

sealed class AuthState {

    data object Authed: AuthState()

    data object NeedEmailCode: AuthState()

    data object NeedTFA: AuthState()

    data object NeedTTFA: AuthState()

    data class Unauthorized(val message: String): AuthState()
}
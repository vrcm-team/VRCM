package io.github.vrcmteam.vrcm.network.api.attributes

enum class AuthState {
    Authed,
    NeedEmailCode,
    NeedTFA,
    NeedTTFA,
    Unauthorized
}
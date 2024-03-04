package io.github.vrcmteam.vrcm.network.api.attributes

enum class AuthType(val typeName: String) {

    Email("emailOtp"),

    TFA("otp"),

    TTFA("totp");

}

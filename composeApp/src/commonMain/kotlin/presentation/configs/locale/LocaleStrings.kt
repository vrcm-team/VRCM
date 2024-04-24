package io.github.vrcmteam.vrcm.presentation.configs.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.intl.Locale

@Stable
data class LocaleStrings(
    val authLoginTitle: String = "Login",
    val authLoginButton: String = "LOGIN",
    val authLoginUsername: String = "Username",
    val authLoginPassword: String = "Password",
    val authVerifyTitle: String = "Verify",
    val authVerifyButton: String = "VERIFY",
    val fiendLocationPagerWebsite: String = "Active on the Website",
    val fiendLocationPagerPrivate: String = "Friends in Private Worlds",
    val fiendLocationPagerTraveling: String = "Friends is Traveling",
    val fiendLocationPagerLocation: String = "by Location",
)

val strings: LocaleStrings
    @Composable
    get() {
        Locale.current.language
        return when (LocalLanguageTag.current.value) {
            LanguageTag.EN -> LocaleStringsEN
            LanguageTag.JA -> LocaleStringsJA
            LanguageTag.ZH_HANS -> LocaleStringsZH
            else -> LocaleStringsEN
        }
    }


package io.github.vrcmteam.vrcm.presentation.configs.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import io.github.vrcmteam.vrcm.presentation.configs.LocalConfiguration

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
    val stettingLanguage: String = "Language:",
    val stettingThemeMode: String = "ThemeMode:",
    val stettingSystemThemeMode: String = "System",
    val stettingLightThemeMode: String = "Light",
    val stettingDarkThemeMode: String = "Dark",
    val stettingThemeColor: String = "ThemeColor:",
)
val strings: LocaleStrings
    @Composable
    get() {
        return when (LocalConfiguration.current.value.languageTag) {
            LanguageTag.EN -> LocaleStringsEn
            LanguageTag.JA -> LocaleStringsJa
            LanguageTag.ZH_HANS -> LocaleStringsZhHans
            LanguageTag.ZH_HANT -> LocaleStringsZhHant
            else -> LocaleStringsEn
        }
    }


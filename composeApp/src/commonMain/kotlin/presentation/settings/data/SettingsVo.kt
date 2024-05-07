package io.github.vrcmteam.vrcm.presentation.settings.data

import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor

data class SettingsVo(
    val isDarkTheme: Boolean?,
    val languageTag: LanguageTag,
    val themeColor: ThemeColor,
)
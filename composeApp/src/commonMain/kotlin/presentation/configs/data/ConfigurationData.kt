package io.github.vrcmteam.vrcm.presentation.configs.data

import io.github.vrcmteam.vrcm.presentation.configs.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.configs.theme.ThemeColor

data class ConfigurationData(
    val isDarkTheme: Boolean?,
    val languageTag: LanguageTag,
    val themeColor: ThemeColor,
)
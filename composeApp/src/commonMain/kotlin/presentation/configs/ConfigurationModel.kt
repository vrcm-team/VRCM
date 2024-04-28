package io.github.vrcmteam.vrcm.presentation.configs

import io.github.vrcmteam.vrcm.presentation.configs.data.ConfigurationData
import io.github.vrcmteam.vrcm.presentation.configs.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.configs.theme.ThemeColor
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.github.vrcmteam.vrcm.storage.data.SettingsData

class ConfigurationModel(
    private val settingsDao: SettingsDao,
    private val themeColors: List<ThemeColor>
) {
    fun saveConfiguration(configurationData: ConfigurationData) {
        settingsDao.saveSettings(configurationData.let {
            SettingsData(
                isDarkTheme = it.isDarkTheme,
                themeColor = it.themeColor.name,
                languageTag = it.languageTag.tag
            )
        })
    }

    val configurationData: ConfigurationData
        get() {
            val settings = settingsDao.settings()
            val languageTag = settings.languageTag?.let { LanguageTag.fromTag(it) } ?: LanguageTag.Default
            val themeColor = settings.themeColor?.let { name -> themeColors.firstOrNull { it.name == name } }
                    ?: ThemeColor.Default
            return ConfigurationData(
                isDarkTheme = settings.isDarkTheme,
                themeColor = themeColor,
                languageTag = languageTag,
            )
        }
}
package io.github.vrcmteam.vrcm.presentation.settings

import io.github.vrcmteam.vrcm.presentation.settings.data.SettingsVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.github.vrcmteam.vrcm.storage.data.SettingsData

class SettingsModel(
    private val settingsDao: SettingsDao,
    private val themeColors: List<ThemeColor>
) {
    fun saveSettings(settingsVo: SettingsVo) {
        settingsDao.saveSettings(settingsVo.let {
            SettingsData(
                isDarkTheme = it.isDarkTheme,
                themeColor = it.themeColor.name,
                languageTag = it.languageTag.tag
            )
        })
    }

    val settingsVo: SettingsVo
        get() {
            val settings = settingsDao.settings()
            val languageTag = settings.languageTag?.let { LanguageTag.fromTag(it) } ?: LanguageTag.Default
            val themeColor = settings.themeColor?.let { name -> themeColors.firstOrNull { it.name == name } }
                    ?: ThemeColor.Default
            return SettingsVo(
                isDarkTheme = settings.isDarkTheme,
                themeColor = themeColor,
                languageTag = languageTag,
            )
        }
}
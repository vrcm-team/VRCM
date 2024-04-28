package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.storage.data.SettingsData

class SettingsDao(
    private val settingsSettings: Settings
)  {

    fun settings(): SettingsData {
        return SettingsData(
            isDarkTheme = settingsSettings.getBooleanOrNull(DaoKeys.Settings.IS_DARK_THEME_KEY),
            themeColor = settingsSettings.getStringOrNull(DaoKeys.Settings.THEME_COLOR_KEY),
            languageTag = settingsSettings.getStringOrNull(DaoKeys.Settings.LANGUAGE_TAG_KEY)
        )
    }

    fun saveSettings(settings: SettingsData) {
        settings.isDarkTheme?.let {
            settingsSettings.putBoolean(DaoKeys.Settings.IS_DARK_THEME_KEY, it)
        }?:settingsSettings.remove(DaoKeys.Settings.IS_DARK_THEME_KEY)

        settings.themeColor?.let {
            settingsSettings.putString(DaoKeys.Settings.THEME_COLOR_KEY, it)
        }

        settings.languageTag?.let {
            settingsSettings.putString(DaoKeys.Settings.LANGUAGE_TAG_KEY, it)
        }
    }

}

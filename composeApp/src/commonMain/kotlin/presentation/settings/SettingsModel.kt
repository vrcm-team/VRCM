package io.github.vrcmteam.vrcm.presentation.settings

import io.github.vrcmteam.vrcm.presentation.settings.data.SettingsVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.github.vrcmteam.vrcm.storage.data.SettingsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsModel(
    private val settingsDao: SettingsDao,
    private val themeColors: List<ThemeColor>
) {
    private val _backgroundMonitoringUnavailable = MutableStateFlow(false)

    /**
     * True while a refused background monitoring start is still waiting to be applied.
     *
     * Whoever owns the in-memory settings state turns this into a settings change, so the switch is
     * corrected through the same flow as any user edit instead of writing the DAO behind its back.
     * Holding it as state keeps a report raised before the settings UI observes, and clearing it in
     * [consumeBackgroundMonitoringUnavailable] stops it from being applied again on every
     * recomposition or Activity recreation.
     */
    val backgroundMonitoringUnavailable: StateFlow<Boolean> = _backgroundMonitoringUnavailable.asStateFlow()

    fun reportBackgroundMonitoringUnavailable() {
        _backgroundMonitoringUnavailable.value = true
    }

    fun consumeBackgroundMonitoringUnavailable() {
        _backgroundMonitoringUnavailable.value = false
    }

    fun saveSettings(settingsVo: SettingsVo) {
        settingsDao.settings = settingsVo.let {
            SettingsData(
                isDarkTheme = it.isDarkTheme,
                themeColor = it.themeColor.name,
                languageTag = it.languageTag.tag,
                friendPresenceNotificationsEnabled = it.friendPresenceNotificationsEnabled,
                boopNotificationsEnabled = it.boopNotificationsEnabled,
                backgroundFriendMonitoringEnabled = it.backgroundFriendMonitoringEnabled,
            )
        }
    }

    val settingsVo: SettingsVo
        get() {
            val settings = settingsDao.settings
            val languageTag = settings.languageTag?.let { LanguageTag.fromTag(it) } ?: LanguageTag.Default
            val themeColor = settings.themeColor?.let { name -> themeColors.firstOrNull { it.name == name } }
                    ?: ThemeColor.Default
            return SettingsVo(
                isDarkTheme = settings.isDarkTheme,
                themeColor = themeColor,
                languageTag = languageTag,
                friendPresenceNotificationsEnabled = settings.friendPresenceNotificationsEnabled,
                boopNotificationsEnabled = settings.boopNotificationsEnabled,
                backgroundFriendMonitoringEnabled = settings.backgroundFriendMonitoringEnabled,
            )
        }
}

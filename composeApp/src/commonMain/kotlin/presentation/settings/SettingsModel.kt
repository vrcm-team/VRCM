package io.github.vrcmteam.vrcm.presentation.settings

import io.github.vrcmteam.vrcm.presentation.settings.data.SettingsVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.github.vrcmteam.vrcm.storage.data.SettingsData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class SettingsModel(
    private val settingsDao: SettingsDao,
    private val themeColors: List<ThemeColor>
) {
    private val _backgroundMonitoringUnavailable = MutableSharedFlow<Unit>(replay = 1)

    /**
     * Emitted when the platform refused to run background monitoring.
     *
     * Whoever owns the in-memory settings state turns this into a settings change, so the switch is
     * corrected through the same flow as any user edit instead of writing the DAO behind its back.
     * Replay keeps the report if it happens before the settings UI starts observing.
     */
    val backgroundMonitoringUnavailable: SharedFlow<Unit> = _backgroundMonitoringUnavailable.asSharedFlow()

    fun reportBackgroundMonitoringUnavailable() {
        _backgroundMonitoringUnavailable.tryEmit(Unit)
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

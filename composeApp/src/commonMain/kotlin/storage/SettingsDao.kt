package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.storage.data.SettingsData

class SettingsDao(
    private val settingsSettings: Settings
) {

    var settings: SettingsData
        get() {
            return SettingsData(
                isDarkTheme = settingsSettings.getBooleanOrNull(DaoKeys.Settings.IS_DARK_THEME_KEY),
                themeColor = settingsSettings.getStringOrNull(DaoKeys.Settings.THEME_COLOR_KEY),
                languageTag = settingsSettings.getStringOrNull(DaoKeys.Settings.LANGUAGE_TAG_KEY),
                friendPresenceNotificationsEnabled = friendPresenceNotificationsEnabled,
                boopNotificationsEnabled = boopNotificationsEnabled,
                backgroundFriendMonitoringEnabled = backgroundFriendMonitoringEnabled,
            )
        }
        set(value) {
            value.isDarkTheme?.let {
                settingsSettings.putBoolean(DaoKeys.Settings.IS_DARK_THEME_KEY, it)
            } ?: settingsSettings.remove(DaoKeys.Settings.IS_DARK_THEME_KEY)

            value.themeColor?.let {
                settingsSettings.putString(DaoKeys.Settings.THEME_COLOR_KEY, it)
            }

            value.languageTag?.let {
                settingsSettings.putString(DaoKeys.Settings.LANGUAGE_TAG_KEY, it)
            }
            friendPresenceNotificationsEnabled = value.friendPresenceNotificationsEnabled
            boopNotificationsEnabled = value.boopNotificationsEnabled
            backgroundFriendMonitoringEnabled = value.backgroundFriendMonitoringEnabled
        }

    var rememberVersion: String?
        get() = settingsSettings.getStringOrNull(DaoKeys.Settings.REMEMBER_VERSION_KEY)
        set(value) = value.let {
            if (!it.isNullOrEmpty()) {
                settingsSettings.putString(DaoKeys.Settings.REMEMBER_VERSION_KEY, it)
            } else {
                settingsSettings.remove(DaoKeys.Settings.REMEMBER_VERSION_KEY)
            }
        }

    var friendPresenceNotificationsEnabled: Boolean
        get() = settingsSettings.getBoolean(DaoKeys.Settings.FRIEND_PRESENCE_NOTIFICATIONS_ENABLED_KEY, false)
        set(value) = settingsSettings.putBoolean(DaoKeys.Settings.FRIEND_PRESENCE_NOTIFICATIONS_ENABLED_KEY, value)

    var boopNotificationsEnabled: Boolean
        get() = settingsSettings.getBoolean(DaoKeys.Settings.BOOP_NOTIFICATIONS_ENABLED_KEY, false)
        set(value) = settingsSettings.putBoolean(DaoKeys.Settings.BOOP_NOTIFICATIONS_ENABLED_KEY, value)

    var backgroundFriendMonitoringEnabled: Boolean
        get() = settingsSettings.getBoolean(DaoKeys.Settings.BACKGROUND_FRIEND_MONITORING_ENABLED_KEY, false)
        set(value) = settingsSettings.putBoolean(DaoKeys.Settings.BACKGROUND_FRIEND_MONITORING_ENABLED_KEY, value)
}

package io.github.vrcmteam.vrcm.storage

import com.russhwolf.settings.Settings
import io.github.vrcmteam.vrcm.service.FriendPresenceFilter
import io.github.vrcmteam.vrcm.storage.data.SettingsData
import kotlinx.serialization.json.Json

private val filterJson = Json { ignoreUnknownKeys = true }

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
                friendOfflineNotificationsEnabled = friendOfflineNotificationsEnabled,
                boopNotificationsEnabled = boopNotificationsEnabled,
                friendRequestNotificationsEnabled = friendRequestNotificationsEnabled,
                groupAnnouncementNotificationsEnabled = groupAnnouncementNotificationsEnabled,
                friendPresenceFilter = friendPresenceFilter,
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
            friendOfflineNotificationsEnabled = value.friendOfflineNotificationsEnabled
            boopNotificationsEnabled = value.boopNotificationsEnabled
            friendRequestNotificationsEnabled = value.friendRequestNotificationsEnabled
            groupAnnouncementNotificationsEnabled = value.groupAnnouncementNotificationsEnabled
            friendPresenceFilter = value.friendPresenceFilter
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

    /** 下线提醒默认关闭：多数人只关心谁上线了。 */
    var friendOfflineNotificationsEnabled: Boolean
        get() = settingsSettings.getBoolean(DaoKeys.Settings.FRIEND_OFFLINE_NOTIFICATIONS_ENABLED_KEY, false)
        set(value) = settingsSettings.putBoolean(DaoKeys.Settings.FRIEND_OFFLINE_NOTIFICATIONS_ENABLED_KEY, value)

    var boopNotificationsEnabled: Boolean
        get() = settingsSettings.getBoolean(DaoKeys.Settings.BOOP_NOTIFICATIONS_ENABLED_KEY, false)
        set(value) = settingsSettings.putBoolean(DaoKeys.Settings.BOOP_NOTIFICATIONS_ENABLED_KEY, value)

    var friendRequestNotificationsEnabled: Boolean
        get() = settingsSettings.getBoolean(DaoKeys.Settings.FRIEND_REQUEST_NOTIFICATIONS_ENABLED_KEY, false)
        set(value) = settingsSettings.putBoolean(DaoKeys.Settings.FRIEND_REQUEST_NOTIFICATIONS_ENABLED_KEY, value)

    var groupAnnouncementNotificationsEnabled: Boolean
        get() = settingsSettings.getBoolean(DaoKeys.Settings.GROUP_ANNOUNCEMENT_NOTIFICATIONS_ENABLED_KEY, false)
        set(value) = settingsSettings.putBoolean(DaoKeys.Settings.GROUP_ANNOUNCEMENT_NOTIFICATIONS_ENABLED_KEY, value)

    /** 名单是结构化数据，Settings 只能存字符串，因此整体序列化；解析失败按默认值处理。 */
    var friendPresenceFilter: FriendPresenceFilter
        get() = settingsSettings.getStringOrNull(DaoKeys.Settings.PRESENCE_FILTER_KEY)
            ?.let { runCatching { filterJson.decodeFromString<FriendPresenceFilter>(it) }.getOrNull() }
            ?: FriendPresenceFilter.Default
        set(value) = settingsSettings.putString(
            DaoKeys.Settings.PRESENCE_FILTER_KEY,
            filterJson.encodeToString(value),
        )

    var backgroundFriendMonitoringEnabled: Boolean
        get() = settingsSettings.getBoolean(DaoKeys.Settings.BACKGROUND_FRIEND_MONITORING_ENABLED_KEY, false)
        set(value) = settingsSettings.putBoolean(DaoKeys.Settings.BACKGROUND_FRIEND_MONITORING_ENABLED_KEY, value)
}

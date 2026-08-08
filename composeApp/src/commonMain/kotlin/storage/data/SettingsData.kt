package io.github.vrcmteam.vrcm.storage.data

import kotlinx.serialization.Serializable

@Serializable
data class SettingsData(
    val isDarkTheme: Boolean?,
    val themeColor: String?,
    val languageTag: String?,
    val friendPresenceNotificationsEnabled: Boolean = false,
    val boopNotificationsEnabled: Boolean = false,
    val backgroundFriendMonitoringEnabled: Boolean = false,
)

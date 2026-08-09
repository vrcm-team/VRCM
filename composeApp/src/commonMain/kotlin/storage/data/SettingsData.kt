package io.github.vrcmteam.vrcm.storage.data

import io.github.vrcmteam.vrcm.service.FriendPresenceFilter
import kotlinx.serialization.Serializable

@Serializable
data class SettingsData(
    val isDarkTheme: Boolean?,
    val themeColor: String?,
    val languageTag: String?,
    val friendPresenceNotificationsEnabled: Boolean = false,
    val friendOfflineNotificationsEnabled: Boolean = false,
    val boopNotificationsEnabled: Boolean = false,
    val friendRequestNotificationsEnabled: Boolean = false,
    val groupAnnouncementNotificationsEnabled: Boolean = false,
    val friendPresenceFilter: FriendPresenceFilter = FriendPresenceFilter.Default,
    val backgroundFriendMonitoringEnabled: Boolean = false,
)

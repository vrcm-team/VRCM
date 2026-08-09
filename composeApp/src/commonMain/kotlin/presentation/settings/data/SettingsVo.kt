package io.github.vrcmteam.vrcm.presentation.settings.data

import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.service.FriendPresenceFilter

data class SettingsVo(
    val isDarkTheme: Boolean?,
    val languageTag: LanguageTag,
    val themeColor: ThemeColor,
    val friendPresenceNotificationsEnabled: Boolean = false,
    val friendOfflineNotificationsEnabled: Boolean = false,
    val boopNotificationsEnabled: Boolean = false,
    val friendRequestNotificationsEnabled: Boolean = false,
    val groupAnnouncementNotificationsEnabled: Boolean = false,
    val friendPresenceFilter: FriendPresenceFilter = FriendPresenceFilter.Default,
    val backgroundFriendMonitoringEnabled: Boolean = false,
)

package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.BackgroundFriendMonitoringResult
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.settings.rememberNotificationPermissionRequester
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.service.FavoriteService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.PresenceFilterMode
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/**
 * 通知设置详情页。
 *
 * 设置面板里只留一个入口，具体开关都收在这里；页面本身只在能真正投递通知的平台可达。
 */
@Serializable
object NotificationSettingsScreen : AppDetailRoute {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val platform = koinInject<AppPlatform>()
        val favoriteService = koinInject<FavoriteService>()
        val friendService = koinInject<FriendService>()
        var currentSettings by LocalSettingsState.current

        LaunchedEffect(Unit) { favoriteService.loadFavoriteByGroup(FavoriteType.Friend) }
        val favoriteGroups by favoriteService.favoritesByGroup(FavoriteType.Friend).collectAsState()

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(strings.notificationSettingsTitle) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "back",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SectionTitle(strings.notificationSectionFriendPresence)
                }
                item {
                    SettingsSwitchRow(
                        title = strings.stettingFriendPresenceNotifications,
                        description = strings.notificationOnlineDescription,
                        checked = currentSettings.friendPresenceNotificationsEnabled,
                    ) {
                        currentSettings = currentSettings.copy(friendPresenceNotificationsEnabled = it)
                    }
                }
                item {
                    SettingsSwitchRow(
                        title = strings.notificationFriendOffline,
                        description = strings.notificationOfflineDescription,
                        checked = currentSettings.friendOfflineNotificationsEnabled,
                    ) {
                        currentSettings = currentSettings.copy(friendOfflineNotificationsEnabled = it)
                    }
                }

                if (currentSettings.friendPresenceNotificationsEnabled ||
                    currentSettings.friendOfflineNotificationsEnabled
                ) {
                    item { HorizontalDivider() }
                    item { SectionTitle(strings.notificationSectionFilter) }
                    item {
                        Text(
                            text = strings.notificationFilterDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PresenceFilterMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = currentSettings.friendPresenceFilter.mode == mode,
                                    onClick = {
                                        currentSettings = currentSettings.copy(
                                            friendPresenceFilter = currentSettings.friendPresenceFilter.copy(mode = mode),
                                        )
                                    },
                                    label = {
                                        Text(
                                            when (mode) {
                                                PresenceFilterMode.Blacklist -> strings.notificationFilterBlacklist
                                                PresenceFilterMode.Whitelist -> strings.notificationFilterWhitelist
                                            }
                                        )
                                    },
                                )
                            }
                        }
                    }
                    item { SectionTitle(strings.notificationFilterGroups) }
                    items(favoriteGroups.keys.toList(), key = { it.id }) { group ->
                        val selected = group.id in currentSettings.friendPresenceFilter.groupIds
                        SettingsSwitchRow(
                            title = group.displayName.ifBlank { group.name },
                            description = null,
                            checked = selected,
                        ) { checked ->
                            val ids = currentSettings.friendPresenceFilter.groupIds.toMutableSet()
                            if (checked) ids += group.id else ids -= group.id
                            currentSettings = currentSettings.copy(
                                friendPresenceFilter = currentSettings.friendPresenceFilter.copy(groupIds = ids),
                            )
                        }
                    }
                    item { SectionTitle(strings.notificationFilterFriends) }
                    item {
                        Text(
                            text = strings.notificationFilterFriendsDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val favoriteUserIds = favoriteGroups.values.flatten().map { it.favoriteId }.distinct()
                    items(favoriteUserIds, key = { it }) { userId ->
                        val friend = friendService.friendMap[userId]
                        val override = currentSettings.friendPresenceFilter.userOverrides[userId]
                        FriendOverrideRow(
                            name = friend?.displayName?.takeIf(String::isNotBlank) ?: userId,
                            override = override,
                        ) { next ->
                            val overrides = currentSettings.friendPresenceFilter.userOverrides.toMutableMap()
                            if (next == null) overrides -= userId else overrides[userId] = next
                            currentSettings = currentSettings.copy(
                                friendPresenceFilter = currentSettings.friendPresenceFilter.copy(userOverrides = overrides),
                            )
                        }
                    }
                }

                item { HorizontalDivider() }
                item { SectionTitle(strings.notificationSectionInbox) }
                item {
                    SettingsSwitchRow(
                        title = strings.stettingBoopNotifications,
                        description = strings.notificationBoopDescription,
                        checked = currentSettings.boopNotificationsEnabled,
                    ) { currentSettings = currentSettings.copy(boopNotificationsEnabled = it) }
                }
                item {
                    SettingsSwitchRow(
                        title = strings.notificationFriendRequestAlert,
                        description = strings.notificationFriendRequestAlertDescription,
                        checked = currentSettings.friendRequestNotificationsEnabled,
                    ) { currentSettings = currentSettings.copy(friendRequestNotificationsEnabled = it) }
                }
                item {
                    SettingsSwitchRow(
                        title = strings.notificationGroupAnnouncement,
                        description = strings.notificationGroupAnnouncementDescription,
                        checked = currentSettings.groupAnnouncementNotificationsEnabled,
                    ) { currentSettings = currentSettings.copy(groupAnnouncementNotificationsEnabled = it) }
                }

                if (platform.supportsBackgroundFriendMonitoring) {
                    item { HorizontalDivider() }
                    item { BackgroundMonitoringSection(platform) }
                }
            }
        }
    }
}

/**
 * 后台监测：开关加上权限与耗电两项状态。
 *
 * 状态在每次回到前台时重读，因为用户可能刚从系统设置页改完权限回来。
 */
@Composable
private fun BackgroundMonitoringSection(platform: AppPlatform) {
    var currentSettings by LocalSettingsState.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val notificationsAllowed = remember(revision) { platform.hasBackgroundFriendMonitoringPermission() }
    val batteryUnrestricted = remember(revision) { platform.isIgnoringBatteryOptimizations() }
    val permissionRequiredMessage = strings.stettingBackgroundPermissionRequired
    val unavailableMessage = strings.stettingBackgroundUnavailable

    val apply: (Boolean) -> Unit = { enabled ->
        when (platform.setBackgroundFriendMonitoringEnabled(enabled)) {
            BackgroundFriendMonitoringResult.Started,
            BackgroundFriendMonitoringResult.Stopped,
            -> currentSettings = currentSettings.copy(backgroundFriendMonitoringEnabled = enabled)

            BackgroundFriendMonitoringResult.PermissionRequired ->
                scope.launch { SharedFlowCentre.toastText.emit(ToastText.Info(permissionRequiredMessage)) }

            BackgroundFriendMonitoringResult.Unsupported ->
                scope.launch { SharedFlowCentre.toastText.emit(ToastText.Error(unavailableMessage)) }
        }
        revision++
    }
    val requestPermission = rememberNotificationPermissionRequester { granted ->
        if (granted) {
            apply(true)
        } else {
            scope.launch { SharedFlowCentre.toastText.emit(ToastText.Info(permissionRequiredMessage)) }
        }
        revision++
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(strings.notificationSectionBackground)
        SettingsSwitchRow(
            title = strings.stettingBackgroundMonitoring,
            description = strings.notificationBackgroundDescription,
            checked = currentSettings.backgroundFriendMonitoringEnabled,
        ) { enabled ->
            if (enabled && !notificationsAllowed) requestPermission() else apply(enabled)
        }
        if (currentSettings.backgroundFriendMonitoringEnabled) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusRow(
                        label = strings.stettingBackgroundNotifications,
                        value = if (notificationsAllowed) strings.stettingStatusEnabled else strings.stettingStatusDisabled,
                        actionLabel = strings.stettingNotificationSettings,
                        onAction = platform::openNotificationSettings,
                    )
                    if (platform.supportsBatteryOptimizationSettings) {
                        StatusRow(
                            label = strings.stettingBackgroundBattery,
                            value = if (batteryUnrestricted) strings.stettingStatusAllowed else strings.stettingStatusManaged,
                            actionLabel = strings.stettingBatterySettings,
                            onAction = platform::openBatteryOptimizationSettings,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 单个好友是三态：跟随分组、始终提醒、始终不提醒。 */
@Composable
private fun FriendOverrideRow(name: String, override: Boolean?, onChange: (Boolean?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        FilterChip(
            selected = override == null,
            onClick = { onChange(null) },
            label = { Text(strings.notificationOverrideFollowGroup) },
        )
        FilterChip(
            selected = override == true,
            onClick = { onChange(true) },
            label = { Text(strings.notificationOverrideAlways) },
        )
        FilterChip(
            selected = override == false,
            onClick = { onChange(false) },
            label = { Text(strings.notificationOverrideNever) },
        )
    }
}

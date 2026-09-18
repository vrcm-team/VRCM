package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.ImageLoader
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.BackgroundFriendMonitoringResult
import io.github.vrcmteam.vrcm.core.extensions.bytesToMb
import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.attributes.FavoriteType
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppActivityIndicator
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppButtonStyle
import io.github.vrcmteam.vrcm.presentation.designsystem.AppDivider
import io.github.vrcmteam.vrcm.presentation.designsystem.AppGroup
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIcon
import io.github.vrcmteam.vrcm.presentation.designsystem.AppIconButton
import io.github.vrcmteam.vrcm.presentation.designsystem.AppNavBar
import io.github.vrcmteam.vrcm.presentation.designsystem.AppRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppScaffold
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSectionFooter
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSectionHeader
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegment
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegmentedControl
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSegmentedRow
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSheet
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSize
import io.github.vrcmteam.vrcm.presentation.designsystem.AppSpacing
import io.github.vrcmteam.vrcm.presentation.designsystem.AppText
import io.github.vrcmteam.vrcm.presentation.designsystem.AppTheme
import io.github.vrcmteam.vrcm.presentation.designsystem.AppToggle
import io.github.vrcmteam.vrcm.presentation.designsystem.rememberAppSheetState
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import io.github.vrcmteam.vrcm.presentation.navigation.LocalNavigator
import io.github.vrcmteam.vrcm.presentation.navigation.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.screens.auth.AuthAnimeScreen
import io.github.vrcmteam.vrcm.presentation.screens.home.dialog.LogoutConfirmationDialog
import io.github.vrcmteam.vrcm.presentation.settings.LocalResolvedDarkTheme
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.settings.rememberNotificationPermissionRequester
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.presentation.supports.AppIcons
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.FavoriteService
import io.github.vrcmteam.vrcm.service.FriendService
import io.github.vrcmteam.vrcm.service.PresenceFilterMode
import io.github.vrcmteam.vrcm.service.VersionService
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.GroupProfileCacheStore
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheStore
import io.github.vrcmteam.vrcm.storage.meetup.DecorationTemplateCacheDao
import io.github.vrcmteam.vrcm.storage.meetup.MeetupCardAssetStore
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import presentation.compoments.UpdateDialog
import presentation.screens.auth.data.VersionVo

/**
 * 设置页：外观（语言 / 主题模式 / 主题色）、通知、剪贴板、缓存与版本、退出登录。
 *
 * 通知相关的分节只在支持好友动态通知的平台（Android）显示。
 */
@Serializable
object SettingsScreen : AppDetailRoute {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val platform = koinInject<AppPlatform>()
        var currentSettings by LocalSettingsState.current

        // 手机上首页被本页盖住时不在组合里，收不到退出登录事件，所以由本页自己回到登录页。
        LaunchedEffect(Unit) {
            SharedFlowCentre.logout.collect {
                navigator replaceAll AuthAnimeScreen(false)
            }
        }

        AppScaffold(
            topBar = {
                AppNavBar(
                    title = { AppText(strings.drawerSettings) },
                    navigationIcon = {
                        AppIconButton(onClick = { navigator.pop() }) {
                            AppIcon(
                                painter = rememberVectorPainter(AppIcons.ArrowBackIosNew),
                                contentDescription = "back",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            // 分节数量固定且很少，用普通滚动列：版本检查结果等行内状态不会因为滚出屏幕而丢失。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.page, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AppearanceSections()
                if (platform.supportsFriendActivityNotifications) {
                    NotificationSections(platform)
                }
                SettingsGroup {
                    SettingsSwitchRow(
                        title = strings.stettingClipboardReading,
                        description = null,
                        checked = currentSettings.clipboardReadingEnabled,
                    ) { enabled ->
                        currentSettings = currentSettings.copy(clipboardReadingEnabled = enabled)
                    }
                }
                AboutSection(platform)
                LogoutButton()
            }
        }
    }
}

/** 外观：语言、主题模式、主题色，改动立即全 App 生效。 */
@Composable
private fun AppearanceSections() {
    var currentSettings by LocalSettingsState.current
    val isDarkTheme = LocalResolvedDarkTheme.current
    Column {
        AppSectionHeader(strings.stettingLanguage)
        val languages = LanguageTag.entries
        AppSegmentedControl(
            options = languages.map { it.displayName },
            selectedIndex = languages.indexOf(currentSettings.languageTag),
            onSelect = { currentSettings = currentSettings.copy(languageTag = languages[it]) },
        )
    }
    Column {
        AppSectionHeader(strings.stettingThemeMode)
        val modes = listOf(null, true, false)
        AppSegmentedControl(
            options = modes.map {
                when (it) {
                    null -> strings.stettingSystemThemeMode
                    true -> strings.stettingDarkThemeMode
                    false -> strings.stettingLightThemeMode
                }
            },
            selectedIndex = modes.indexOf(currentSettings.isDarkTheme),
            onSelect = { currentSettings = currentSettings.copy(isDarkTheme = modes[it]) },
        )
    }
    SettingsGroup(title = strings.stettingThemeColor) {
        ThemeColorSwatches(
            current = currentSettings.themeColor,
            isDarkTheme = isDarkTheme,
            onSelect = { currentSettings = currentSettings.copy(themeColor = it) },
        )
    }
}

/** 主题色色板：一排 34 dp 圆点（取当前深浅模式下的强调色），选中的画勾 + 外圈；点一下立即全 App 换色。 */
@Composable
private fun ThemeColorSwatches(
    current: ThemeColor,
    isDarkTheme: Boolean,
    onSelect: (ThemeColor) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.row, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeColor.all.forEach { themeColor ->
            val colors = themeColor.colors(isDarkTheme)
            val isSelected = themeColor.name == current.name
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .then(if (isSelected) Modifier.border(2.dp, colors.tint, CircleShape) else Modifier)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(colors.tint)
                    .selectable(
                        selected = isSelected,
                        interactionSource = null,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onSelect(themeColor) },
                    )
                    .semantics { contentDescription = themeColor.name },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    AppIcon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onTint)
                }
            }
        }
    }
}

/** 通知：好友上下线、筛选、收件箱、服务状态与后台监测。只在支持通知的平台组合。 */
@Composable
private fun NotificationSections(platform: AppPlatform) {
    val favoriteService = koinInject<FavoriteService>()
    val friendService = koinInject<FriendService>()
    var currentSettings by LocalSettingsState.current
    var showFriendOverrides by remember { mutableStateOf(false) }

    LaunchedEffect(favoriteService) { favoriteService.loadFavoriteByGroup(FavoriteType.Friend) }
    val favoriteGroups by favoriteService.favoritesByGroup(FavoriteType.Friend).collectAsState()
    val friends by friendService.friendState.collectAsState()
    val favoriteUserIds = remember(favoriteGroups) {
        favoriteGroups.values.flatten().map { it.favoriteId }.distinct()
    }
    val friendOverrideItems = remember(favoriteUserIds, friends) {
        favoriteUserIds.mapNotNull { userId ->
            friends[userId]?.displayName
                ?.takeIf(String::isNotBlank)
                ?.let { displayName -> FriendOverrideItem(userId, displayName) }
        }
    }

    if (showFriendOverrides) {
        FriendOverridesBottomSheet(
            friends = friendOverrideItems,
            overrides = currentSettings.friendPresenceFilter.userOverrides,
            onDismiss = { showFriendOverrides = false },
            onChange = { userId, next ->
                val overrides = currentSettings.friendPresenceFilter.userOverrides.toMutableMap()
                if (next == null) overrides -= userId else overrides[userId] = next
                currentSettings = currentSettings.copy(
                    friendPresenceFilter = currentSettings.friendPresenceFilter.copy(
                        userOverrides = overrides,
                    ),
                )
            },
        )
    }

    SettingsGroup(title = strings.notificationSectionFriendPresence) {
        NotificationPermissionSwitchRow(
            platform = platform,
            title = strings.stettingFriendPresenceNotifications,
            description = strings.notificationOnlineDescription,
            checked = currentSettings.friendPresenceNotificationsEnabled,
        ) {
            currentSettings = currentSettings.copy(friendPresenceNotificationsEnabled = it)
        }
        SettingsRowDivider()
        NotificationPermissionSwitchRow(
            platform = platform,
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
        // 黑名单 / 白名单是二选一：用分段控件，说明放在节脚
        Column {
            AppSectionHeader(strings.notificationSectionFilter)
            val modes = PresenceFilterMode.entries
            AppSegmentedControl(
                options = modes.map { mode ->
                    when (mode) {
                        PresenceFilterMode.Blacklist -> strings.notificationFilterBlacklist
                        PresenceFilterMode.Whitelist -> strings.notificationFilterWhitelist
                    }
                },
                selectedIndex = modes.indexOf(currentSettings.friendPresenceFilter.mode),
                onSelect = { index ->
                    currentSettings = currentSettings.copy(
                        friendPresenceFilter = currentSettings.friendPresenceFilter.copy(mode = modes[index]),
                    )
                },
            )
            AppSectionFooter(strings.notificationFilterDescription)
        }
        SettingsGroup(title = strings.notificationFilterGroups) {
            favoriteGroups.keys.forEach { group ->
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
                SettingsRowDivider()
            }
            SettingsNavigationRow(
                title = strings.notificationFilterFriends,
                description = strings.notificationFilterFriendsDescription,
                onClick = { showFriendOverrides = true },
            )
        }
    }

    SettingsGroup(title = strings.notificationSectionInbox) {
        NotificationPermissionSwitchRow(
            platform = platform,
            title = strings.stettingBoopNotifications,
            description = strings.notificationBoopDescription,
            checked = currentSettings.boopNotificationsEnabled,
        ) {
            currentSettings = currentSettings.copy(boopNotificationsEnabled = it)
        }
        SettingsRowDivider()
        NotificationPermissionSwitchRow(
            platform = platform,
            title = strings.notificationFriendRequestAlert,
            description = strings.notificationFriendRequestAlertDescription,
            checked = currentSettings.friendRequestNotificationsEnabled,
        ) {
            currentSettings = currentSettings.copy(friendRequestNotificationsEnabled = it)
        }
        SettingsRowDivider()
        NotificationPermissionSwitchRow(
            platform = platform,
            title = strings.notificationGroupAnnouncement,
            description = strings.notificationGroupAnnouncementDescription,
            checked = currentSettings.groupAnnouncementNotificationsEnabled,
        ) {
            currentSettings = currentSettings.copy(groupAnnouncementNotificationsEnabled = it)
        }
    }

    SettingsGroup(title = strings.notificationSectionServiceStatus) {
        NotificationPermissionSwitchRow(
            platform = platform,
            title = strings.notificationVrchatStatus,
            description = strings.notificationVrchatStatusDescription,
            checked = currentSettings.vrchatStatusNotificationsEnabled,
        ) {
            currentSettings = currentSettings.copy(vrchatStatusNotificationsEnabled = it)
        }
    }

    if (platform.supportsBackgroundFriendMonitoring) {
        BackgroundMonitoringSection(platform)
    }
}

/** 缓存、版本检查与项目主页。 */
@Composable
private fun AboutSection(platform: AppPlatform) {
    val versionService = koinInject<VersionService>()
    val imageLoader = koinInject<ImageLoader>()
    val accountCacheManager = koinInject<AccountCacheManager>()
    val groupProfileCacheStore = koinInject<GroupProfileCacheStore>()
    val worldProfileCacheStore = koinInject<WorldProfileCacheStore>()
    val decorationTemplateCacheDao = koinInject<DecorationTemplateCacheDao>()
    val meetupCardAssetStore = koinInject<MeetupCardAssetStore>()
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(VersionVo()) }
    // 不能直接version.not()因为默认为false会导致一点开就显示
    var isLatestVersion by remember { mutableStateOf(false) }
    var isLoadingVersion by remember { mutableStateOf(false) }
    val checkVersion = {
        scope.launch {
            if (isLoadingVersion) return@launch
            isLoadingVersion = true
            versionService.checkVersion(false).onSuccess {
                isLatestVersion = it.hasNewVersion.not()
                version = VersionVo(
                    it.tagName,
                    it.htmlUrl,
                    it.body,
                    it.hasNewVersion,
                    it.downloadUrl
                )
            }.onApiFailure("Setting") {
                SharedFlowCentre.toastText.emit(ToastText.Error(it))
            }
            isLoadingVersion = false
        }
    }
    SettingsGroup {
        val diskCache = imageLoader.diskCache
        var size by remember(diskCache) { mutableStateOf(diskCache?.size ?: 0L) }
        var isClearingCache by remember { mutableStateOf(false) }
        AppRow(
            title = strings.stettingClearCache,
            value = diskCache?.let { "${size.bytesToMb()}/${it.maxSize.bytesToMb()}MB" },
            enabled = !isClearingCache,
            onClick = {
                scope.launch {
                    isClearingCache = true
                    try {
                        diskCache?.clear()
                        accountCacheManager.clearAll()
                        groupProfileCacheStore.clearAll()
                        worldProfileCacheStore.clearAll()
                        // 普通清缓存只删远端装饰副本；身份牌配置与相册照片保留。
                        decorationTemplateCacheDao.clearAll()
                        meetupCardAssetStore.clearDecorationCache()
                        size = 0
                    } finally {
                        isClearingCache = false
                    }
                }
            },
        )
        SettingsRowDivider()
        AppRow(
            title = strings.stettingVersion,
            value = if (isLatestVersion) "${AppConst.APP_VERSION} (${strings.stettingAlreadyLatest})" else AppConst.APP_VERSION,
            trailing = {
                AnimatedVisibility(isLoadingVersion) {
                    AppActivityIndicator(modifier = Modifier.size(20.dp))
                }
            },
            onClick = { checkVersion() },
        )
        SettingsRowDivider()
        AppRow(
            title = strings.stettingAbout,
            trailing = {
                AppIcon(
                    modifier = Modifier.size(20.dp),
                    imageVector = WebIcons.GithubIcon,
                    contentDescription = "GithubIcon",
                    tint = AppTheme.colors.secondaryLabel,
                )
                AppText(text = "GitHub", color = AppTheme.colors.secondaryLabel)
            },
            chevron = true,
            onClick = { platform.openUrl(AppConst.APP_GITHUB_URL) },
        )
    }
    if (!isLatestVersion) {
        UpdateDialog(
            version = version,
            onDismissRequest = { version = VersionVo() }
        )
    }
}

@Composable
private fun LogoutButton() {
    val authService = koinInject<AuthService>()
    val scope = rememberCoroutineScope()
    var showConfirmation by remember { mutableStateOf(false) }
    // 设置页惯用的破坏性操作行：独占一组、红字居中
    AppGroup {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = null,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = { showConfirmation = true },
                )
                .settingsRowPadding(),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = strings.stettingLogout,
                style = AppTheme.type.body,
                color = AppTheme.colors.destructive,
            )
        }
    }
    if (showConfirmation) {
        LogoutConfirmationDialog(
            onDismissRequest = { showConfirmation = false },
            onConfirm = {
                showConfirmation = false
                scope.launch { authService.logout() }
            },
        )
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

    SettingsGroup(title = strings.notificationSectionBackground) {
        SettingsSwitchRow(
            title = strings.stettingBackgroundMonitoring,
            description = strings.notificationBackgroundDescription,
            checked = currentSettings.backgroundFriendMonitoringEnabled,
        ) { enabled ->
            if (enabled && !notificationsAllowed) requestPermission() else apply(enabled)
        }
        if (currentSettings.backgroundFriendMonitoringEnabled) {
            SettingsRowDivider()
            StatusRow(
                label = strings.stettingBackgroundNotifications,
                value = if (notificationsAllowed) strings.stettingStatusEnabled else strings.stettingStatusDisabled,
                actionLabel = strings.stettingAppManagement,
                onAction = platform::openAppSettings,
            )
            if (platform.supportsBatteryOptimizationSettings) {
                SettingsRowDivider()
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

@Composable
private fun StatusRow(label: String, value: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().settingsRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(label, style = AppTheme.type.body)
            AppText(
                text = value,
                style = AppTheme.type.footnote,
                color = AppTheme.colors.secondaryLabel,
            )
        }
        AppButton(onClick = onAction, style = AppButtonStyle.Plain, size = AppButtonSize.Small) { AppText(actionLabel) }
    }
}

/** 一节设置：可选的节标题 + 一张分组卡片，行与行之间用 [SettingsRowDivider]。 */
@Composable
private fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        if (title != null) AppSectionHeader(title)
        AppGroup(content = content)
    }
}

@Composable
private fun SettingsRowDivider() {
    AppDivider(modifier = Modifier.padding(start = AppSpacing.row))
}

private fun Modifier.settingsRowPadding(): Modifier =
    defaultMinSize(minHeight = AppSize.rowMinHeight).padding(horizontal = AppSpacing.row, vertical = 10.dp)

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().settingsRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(title, style = AppTheme.type.body)
            if (description != null) {
                AppText(
                    text = description,
                    style = AppTheme.type.footnote,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
        }
        AppToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NotificationPermissionSwitchRow(
    platform: AppPlatform,
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val permissionRequiredMessage = strings.stettingBackgroundPermissionRequired
    val requestPermission = rememberNotificationPermissionRequester { granted ->
        if (granted) {
            onCheckedChange(true)
        } else {
            scope.launch {
                SharedFlowCentre.toastText.emit(ToastText.Info(permissionRequiredMessage))
            }
        }
    }
    SettingsSwitchRow(
        title = title,
        description = description,
        checked = checked,
    ) { enabled ->
        if (!enabled || platform.hasBackgroundFriendMonitoringPermission()) {
            onCheckedChange(enabled)
        } else {
            requestPermission()
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    AppRow(
        title = title,
        subtitle = description,
        chevron = true,
        onClick = onClick,
    )
}

private data class FriendOverrideItem(
    val userId: String,
    val displayName: String,
)

@Composable
private fun FriendOverridesBottomSheet(
    friends: List<FriendOverrideItem>,
    overrides: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onChange: (String, Boolean?) -> Unit,
) {
    val listNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(x = 0f, y = available.y)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(x = 0f, y = available.y)
        }
    }

    AppSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberAppSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            AppText(
                text = strings.notificationFilterFriends,
                style = AppTheme.type.title2,
                color = AppTheme.colors.tint,
            )
            AppText(
                text = strings.notificationFilterFriendsDescription,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                style = AppTheme.type.caption1,
                color = AppTheme.colors.secondaryLabel,
            )
            if (friends.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = strings.userCreatedEmpty,
                        color = AppTheme.colors.secondaryLabel,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(listNestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(
                        items = friends,
                        key = { _, friend -> friend.userId },
                    ) { index, friend ->
                        if (index > 0) {
                            AppDivider(color = AppTheme.colors.separator)
                        }
                        FriendOverrideRow(
                            name = friend.displayName,
                            override = overrides[friend.userId],
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) { next -> onChange(friend.userId, next) }
                    }
                }
            }
        }
    }
}

/** 单个好友是三态：跟随分组、始终提醒、始终不提醒。 */
@Composable
private fun FriendOverrideRow(
    name: String,
    override: Boolean?,
    modifier: Modifier = Modifier,
    onChange: (Boolean?) -> Unit,
) {
    val options = listOf(
        null to strings.notificationOverrideFollowGroupShort,
        true to strings.notificationOverrideAlwaysShort,
        false to strings.notificationOverrideNeverShort,
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppText(
            text = name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = AppTheme.type.subheadline,
        )
        AppSegmentedRow(modifier = Modifier.width(210.dp)) {
            options.forEachIndexed { index, (value, label) ->
                AppSegment(
                    selected = value == override,
                    onClick = { onChange(value) },
                ) {
                    AppText(label, maxLines = 1)
                }
            }
        }
    }
}

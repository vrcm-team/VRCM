package io.github.vrcmteam.vrcm.presentation.screens.home.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.ImageLoader
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.BackgroundFriendMonitoringResult
import io.github.vrcmteam.vrcm.core.extensions.bytesToMb
import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState
import io.github.vrcmteam.vrcm.presentation.settings.rememberNotificationPermissionRequester
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.VersionService
import io.github.vrcmteam.vrcm.storage.AccountCacheManager
import io.github.vrcmteam.vrcm.storage.GroupProfileCacheDao
import io.github.vrcmteam.vrcm.storage.WorldProfileCacheDao
import kotlinx.coroutines.launch
import org.koin.compose.currentKoinScope
import org.koin.compose.koinInject
import presentation.compoments.UpdateDialog
import presentation.screens.auth.data.VersionVo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
) {

    ABottomSheet(
        isVisible = isVisible,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismissRequest
    ) {

        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsBlockSurface {
                CustomBlock()
            }
            SettingsBlockSurface {
                AboutBlock()
            }
            LogoutButton(onDismissRequest)
        }
    }
}


@Composable
private inline fun ColumnScope.CustomBlock() {
    var currentSettings by LocalSettingsState.current
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsItem("${strings.stettingLanguage}:") {
            LanguageTag.entries.forEach {
                TextButton(
                    enabled = it.tag != currentSettings.languageTag.tag,
                    onClick = {
                        currentSettings = currentSettings.copy(languageTag = it)
                    }
                ) {
                    Text(
                        text = it.displayName,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
        SettingsItem("${strings.stettingThemeMode}:") {
            listOf(null, true, false).forEach {
                TextButton(enabled = currentSettings.isDarkTheme != it, onClick = {
                    currentSettings = currentSettings.copy(isDarkTheme = it)
                }) {
                    Text(
                        text = when (it) {
                            null -> strings.stettingSystemThemeMode
                            true -> strings.stettingDarkThemeMode
                            false -> strings.stettingLightThemeMode
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
        val themeColors: List<ThemeColor> = with(currentKoinScope()) { remember(::getAll) }
        SettingsItem("${strings.stettingThemeColor}:") {
            themeColors.forEach {
                TextButton(
                    enabled = it.name != currentSettings.themeColor.name,
                    onClick = {
                        currentSettings = currentSettings.copy(themeColor = it)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = it.colorScheme.primaryContainer,
                        contentColor = it.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        text = it.name,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
        val platform = koinInject<AppPlatform>()
        if (platform.supportsFriendActivityNotifications) {
            Text(strings.stettingFriendActivity, style = MaterialTheme.typography.titleMedium)
            ToggleSettingsRow(
                title = strings.stettingFriendPresenceNotifications,
                checked = currentSettings.friendPresenceNotificationsEnabled,
            ) { currentSettings = currentSettings.copy(friendPresenceNotificationsEnabled = it) }
            ToggleSettingsRow(
                title = strings.stettingBoopNotifications,
                checked = currentSettings.boopNotificationsEnabled,
            ) { currentSettings = currentSettings.copy(boopNotificationsEnabled = it) }
        }
        if (platform.supportsBackgroundFriendMonitoring) {
            var backgroundSettingsRevision by remember { mutableIntStateOf(0) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { backgroundSettingsRevision++ }
            val notificationsAllowed = remember(backgroundSettingsRevision) {
                platform.hasBackgroundFriendMonitoringPermission()
            }
            val batteryUnrestricted = remember(backgroundSettingsRevision) {
                platform.isIgnoringBatteryOptimizations()
            }
            val permissionRequiredMessage = strings.stettingBackgroundPermissionRequired
            val unavailableMessage = strings.stettingBackgroundUnavailable
            // toastText has no replay or buffer, so tryEmit would silently drop these notices and
            // leave the user without any feedback; emit from a scope like the rest of the app.
            val toastScope = rememberCoroutineScope()
            val updateBackgroundMonitoring: (Boolean) -> Unit = { enabled ->
                when (platform.setBackgroundFriendMonitoringEnabled(enabled)) {
                    BackgroundFriendMonitoringResult.Started,
                    BackgroundFriendMonitoringResult.Stopped,
                    -> currentSettings = currentSettings.copy(backgroundFriendMonitoringEnabled = enabled)
                    BackgroundFriendMonitoringResult.PermissionRequired -> {
                        toastScope.launch {
                            SharedFlowCentre.toastText.emit(ToastText.Info(permissionRequiredMessage))
                        }
                    }
                    BackgroundFriendMonitoringResult.Unsupported -> {
                        toastScope.launch {
                            SharedFlowCentre.toastText.emit(ToastText.Error(unavailableMessage))
                        }
                    }
                }
                backgroundSettingsRevision++
            }
            val requestNotificationPermission = rememberNotificationPermissionRequester { granted ->
                if (granted) {
                    updateBackgroundMonitoring(true)
                } else {
                    toastScope.launch {
                        SharedFlowCentre.toastText.emit(ToastText.Info(permissionRequiredMessage))
                    }
                }
                backgroundSettingsRevision++
            }
            ToggleSettingsRow(
                title = strings.stettingBackgroundMonitoring,
                checked = currentSettings.backgroundFriendMonitoringEnabled,
            ) { enabled ->
                if (enabled && !notificationsAllowed) requestNotificationPermission()
                else updateBackgroundMonitoring(enabled)
            }
            if (currentSettings.backgroundFriendMonitoringEnabled) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.stettingBackgroundSettings, style = MaterialTheme.typography.titleSmall)
                        Text("${strings.stettingBackgroundNotifications}: ${if (notificationsAllowed) strings.stettingStatusEnabled else strings.stettingStatusDisabled}")
                        Text("${strings.stettingBackgroundBattery}: ${if (batteryUnrestricted) strings.stettingStatusAllowed else strings.stettingStatusManaged}")
                        TextButton(onClick = platform::openNotificationSettings) {
                            Text(strings.stettingNotificationSettings)
                        }
                        if (platform.supportsBatteryOptimizationSettings) {
                            TextButton(onClick = platform::openBatteryOptimizationSettings) {
                                Text(strings.stettingBatterySettings)
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun ToggleSettingsRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
@Composable
private fun AboutBlock() {
    val versionService = koinInject<VersionService>()
    val imageLoader = koinInject<ImageLoader>()
    val accountCacheManager = koinInject<AccountCacheManager>()
    val groupProfileCacheDao = koinInject<GroupProfileCacheDao>()
    val worldProfileCacheDao = koinInject<WorldProfileCacheDao>()
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(VersionVo()) }
    // 不能直接version.not()因为默认为false会导致一点开就显示
    var isLatestVersion by remember { mutableStateOf(false) }
    var isLoadingVersion by remember { mutableStateOf(false) }
    val platform = koinInject<AppPlatform>()
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
    Column {
        val diskCache = imageLoader.diskCache
        var size by remember(diskCache) { mutableStateOf(diskCache?.size ?: 0L) }
        var isClearingCache by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = !isClearingCache) {
                    scope.launch {
                        isClearingCache = true
                        try {
                            diskCache?.clear()
                            accountCacheManager.clearAll()
                            groupProfileCacheDao.clearAll()
                            worldProfileCacheDao.clearAll()
                            size = 0
                        } finally {
                            isClearingCache = false
                        }
                    }
                }
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "${strings.stettingClearCache}:")
            Spacer(modifier = Modifier.weight(1f))
            diskCache?.let {
                Text(text = "${size.bytesToMb()}/${it.maxSize.bytesToMb()}MB")
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { checkVersion() }
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "${strings.stettingVersion}:")
            Spacer(modifier = Modifier.weight(1f))
            Text(text = AppConst.APP_VERSION)
            AnimatedVisibility(isLatestVersion) {
                Text(text = "(${strings.stettingAlreadyLatest})")
            }
            AnimatedVisibility(isLoadingVersion) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable {
                    platform.openUrl(AppConst.APP_GITHUB_URL)
                }
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "${strings.stettingAbout}:")
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .size(20.dp),
                imageVector = WebIcons.GithubIcon,
                contentDescription = "GithubIcon",
            )
            Text(text = "GitHub")
        }
        if (!isLatestVersion) {
            UpdateDialog(
                version = version,
                onDismissRequest = { version = VersionVo() }
            )
        }
    }

}

@Composable
private inline fun LogoutButton(crossinline onDismissRequest: () -> Unit) {
    val authService = koinInject<AuthService>()
    val scope = rememberCoroutineScope()
    val logoutCall: () -> Unit = {
            scope.launch {
                authService.logout()
                onDismissRequest()
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.25f))
        TextButton(
            modifier = Modifier.weight(0.5f),
            onClick = logoutCall,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                text = strings.stettingLogout,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.weight(0.25f))
    }
}

@Composable
private inline fun SettingsBlockSurface(
    crossinline content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
    ) {
        content()
    }
}

@Composable
private inline fun SettingsItem(
    title: String,
    content: @Composable RowScope.() -> Unit,
) {

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }

}

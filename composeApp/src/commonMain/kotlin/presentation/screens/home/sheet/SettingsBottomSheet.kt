package io.github.vrcmteam.vrcm.presentation.screens.home.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.compoments.ToastText
import io.github.vrcmteam.vrcm.presentation.extensions.onApiFailure
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.presentation.supports.WebIcons
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.VersionService
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
    }

}

@Composable
private fun AboutBlock() {
    val versionService = koinInject<VersionService>()
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
                    it.hasNewVersion
                )
            }.onApiFailure("Setting") {
                SharedFlowCentre.toastText.emit(ToastText.Error(it))
            }
            isLoadingVersion = false
        }
    }
    Column {
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
    val logoutCall = LocalNavigator.currentOrThrow.let {
        {
            onDismissRequest()
            authService.logout()
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
    crossinline content: @Composable () -> Unit
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
    content: @Composable RowScope.() -> Unit
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
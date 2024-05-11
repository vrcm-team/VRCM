package io.github.vrcmteam.vrcm.presentation.screens.home.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.AppPlatform
import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.extensions.openUrl
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import io.github.vrcmteam.vrcm.service.AuthService
import io.github.vrcmteam.vrcm.service.VersionService
import kotlinx.coroutines.launch
import org.koin.compose.currentKoinScope
import org.koin.compose.koinInject
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
            SettingsBlockSurface(
                padding = 12.dp,
                spacedDp = 4.dp
            ) {
                CustomBlock()
            }

            SettingsBlockSurface(
                padding = 0.dp,
                spacedDp = 0.dp
            ) {
                AboutBlock()
            }
            LogoutButton(onDismissRequest)
        }
    }
}

@Composable
private inline fun ColumnScope.CustomBlock() {
    var currentSettings by LocalSettingsState.current
    SettingsItem(strings.stettingLanguage) {
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
    SettingsItem(strings.stettingThemeMode) {
        listOf(null, true, false).forEach {
            TextButton(
                enabled = currentSettings.isDarkTheme != it,
                onClick = {
                    currentSettings = currentSettings.copy(isDarkTheme = it)
                },

                ) {
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
    SettingsItem(strings.stettingThemeColor) {
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

@Composable
private fun AboutBlock() {
    val versionService = koinInject<VersionService>()
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(VersionVo()) }
    // 不能直接version.not()因为默认为false会导致一点开就显示
    var isLatestVersion by remember { mutableStateOf(false) }
    val platform = koinInject<AppPlatform>()
    Row(
        modifier = Modifier.fillMaxWidth()
         .clip(MaterialTheme.shapes.large)
            .clickable {
                scope.launch {
                    versionService.checkVersion(false).onSuccess {
                        isLatestVersion = it.hasNewVersion.not()
                        version = VersionVo(
                            it.tagName,
                            it.htmlUrl,
                            it.hasNewVersion
                        )
                    }
                }
            }
            .padding(12.dp)
    ) {
        Text(text = strings.stettingVersion)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = AppConst.APP_VERSION)
        AnimatedVisibility(isLatestVersion) {
            Text(text = "(${strings.stettingAlreadyLatest})")
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                platform.openUrl(AppConst.APP_GITHUB_URL)
            }
            .padding(12.dp)
    ) {
        Text(text = strings.stettingAbout)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "GitHub")
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
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
    padding: Dp = 12.dp,
    spacedDp: Dp = 4.dp,
    crossinline content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacedDp)
        ) {
            content()
        }
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
            modifier = Modifier.padding(4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }

}
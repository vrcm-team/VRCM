package io.github.vrcmteam.vrcm.presentation.screens.home.sheet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.vrcmteam.vrcm.presentation.compoments.ABottomSheet
import io.github.vrcmteam.vrcm.presentation.settings.LocalSettingsState
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.locale.strings
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
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
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp)
        ) {
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
            SettingsItem(strings.stettingAbout) {
                val versionService = koinInject<VersionService>()
                val scope = rememberCoroutineScope()
                var version by remember { mutableStateOf(VersionVo()) }

                OutlinedButton(onClick = {
                    scope.launch {
                        versionService. checkVersion(false).onSuccess {
                            version = VersionVo(
                                it.tagName,
                                it.htmlUrl,
                                it.hasNewVersion
                            )
                        }
                    }
                }){
                    Text(text = strings.stettingAboutVersion)
                    Text(text = VersionService.CURRENT_VERSION)
                }
                UpdateDialog(version,{ version = VersionVo() }){
                    versionService.rememberVersion(it)
                }
            }
            LogoutButton(onDismissRequest)

        }
    }
}

@Composable
private fun LogoutButton(onDismissRequest: () -> Unit) {
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
private fun SettingsItem(
    title: String,
    content: @Composable RowScope.() -> Unit
) {
    Surface {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}
package io.github.vrcmteam.vrcm.presentation.settings

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.vrcmteam.vrcm.presentation.settings.data.SettingsVo
import io.github.vrcmteam.vrcm.presentation.settings.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.settings.theme.ThemeColor
import org.koin.compose.koinInject

@Composable
fun SettingsProvider(
    colorAnimationSpec: AnimationSpec<Color> = tween(600),
    content: @Composable () -> Unit
) {
    val settingsModel: SettingsModel = koinInject()

    CompositionLocalProvider(
        LocalSettingsState provides remember { mutableStateOf(settingsModel.settingsVo) }
    ) {
        val settingsState = LocalSettingsState.current
        val currentSettings = settingsState.value
        LaunchedEffect(currentSettings){
            settingsModel.saveSettings(currentSettings)
        }
        // The platform can refuse to start background monitoring after a restart or a re-login.
        // Correcting the in-memory state keeps the switch honest and lets saveSettings persist it,
        // instead of a direct DAO write that this state would silently overwrite later. The report
        // is cleared once applied, so an Activity recreation cannot switch it off a second time
        // after the user has turned it back on.
        LaunchedEffect(Unit) {
            settingsModel.backgroundMonitoringUnavailable.collect { unavailable ->
                if (!unavailable) return@collect
                settingsState.value = settingsState.value.copy(backgroundFriendMonitoringEnabled = false)
                settingsModel.consumeBackgroundMonitoringUnavailable()
            }
        }

        val systemDark = rememberSystemInDarkTheme()
        val isDark = currentSettings.isDarkTheme ?: systemDark
        ChangeStatusBarDarkTheme(isDark)

        CompositionLocalProvider(LocalResolvedDarkTheme provides isDark) {
            MaterialTheme(
                colorScheme = currentSettings.themeColor.asAnimateColorScheme(
                    isDarkTheme = isDark,
                    animationSpec = colorAnimationSpec,
                ),
                shapes = MaterialTheme.shapes,
                typography = MaterialTheme.typography
            ) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    content()
                }
            }
        }
    }

}

// https://issuetracker.google.com/issues/362539765#comment3
@Composable
expect fun ChangeStatusBarDarkTheme(isDark: Boolean)

/** Returns the current platform appearance while handling transient lifecycle values. */
@Composable
expect fun rememberSystemInDarkTheme(): Boolean

/** Requests Android notification permission without retaining an Activity globally. */
@Composable
expect fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit

internal val LocalResolvedDarkTheme = compositionLocalOf { false }

val LocalSettingsState: ProvidableCompositionLocal<MutableState<SettingsVo>> =
    compositionLocalOf {
        mutableStateOf(
            SettingsVo(
                isDarkTheme = null,
                themeColor = ThemeColor.Default,
                languageTag = LanguageTag.Default,
            )
        )
    }

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
        val currentSettings = LocalSettingsState.current.value
        LaunchedEffect(currentSettings){
            settingsModel.saveSettings(currentSettings)
        }
        MaterialTheme(
            colorScheme = currentSettings.themeColor.asAnimateColorScheme(colorAnimationSpec),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography
        ){
            Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                content()
            }
        }
    }

}

val LocalSettingsState: ProvidableCompositionLocal<MutableState<SettingsVo>> =
    compositionLocalOf {
        mutableStateOf(
            SettingsVo(
                isDarkTheme = null,
                themeColor = ThemeColor.Default,
                languageTag = LanguageTag.Default
            )
        )
    }
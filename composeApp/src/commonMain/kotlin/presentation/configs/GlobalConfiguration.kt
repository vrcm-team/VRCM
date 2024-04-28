package io.github.vrcmteam.vrcm.presentation.configs

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import io.github.vrcmteam.vrcm.presentation.configs.data.ConfigurationData
import io.github.vrcmteam.vrcm.presentation.configs.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.configs.theme.ThemeColor
import org.koin.compose.koinInject

@Composable
inline fun Configuration(
    colorAnimationSpec: AnimationSpec<Color> = tween(600),
    noinline content: @Composable () -> Unit
) {
    val configurationModel: ConfigurationModel = koinInject()

    CompositionLocalProvider(
        LocalConfiguration provides remember { mutableStateOf(configurationModel.configurationData) }
    ) {
        val currentConfiguration = LocalConfiguration.current.value
        LaunchedEffect(currentConfiguration){
            configurationModel.saveConfiguration(currentConfiguration)
        }
        MaterialTheme(
            colorScheme = currentConfiguration.themeColor.asAnimateColorScheme(colorAnimationSpec),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content
        )
    }

}

val LocalConfiguration: ProvidableCompositionLocal<MutableState<ConfigurationData>> =
    compositionLocalOf {
        mutableStateOf(
            ConfigurationData(
                isDarkTheme = null,
                themeColor = ThemeColor.Default,
                languageTag = LanguageTag.Default
            )
        )
    }
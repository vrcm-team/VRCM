package io.github.vrcmteam.vrcm.presentation.configs

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.vrcmteam.vrcm.presentation.configs.locale.LanguageTag
import io.github.vrcmteam.vrcm.presentation.configs.locale.LocalLanguageTag
import io.github.vrcmteam.vrcm.presentation.configs.theme.LocalThemeColor
import io.github.vrcmteam.vrcm.presentation.configs.theme.ThemeColor
import io.github.vrcmteam.vrcm.presentation.theme.typography

@Composable
inline fun Configuration(
    themeColor: ThemeColor = ThemeColor.Default,
    colorAnimationSpec: AnimationSpec<Color> = tween(600),
    noinline content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalLanguageTag provides remember { mutableStateOf(LanguageTag.ZH_HANS) },
        LocalThemeColor provides remember { mutableStateOf(themeColor) }
    ) {
        MaterialTheme(
            colorScheme = LocalThemeColor.current.value.asAnimateColorScheme(colorAnimationSpec),
            shapes = MaterialTheme.shapes,
            typography = typography,
            content = content
        )
    }

}
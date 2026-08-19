package io.github.vrcmteam.vrcm.presentation.settings.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


open class ThemeColor(
    val name: String,
    private val lightColorScheme: ColorScheme,
    private val darkColorScheme: ColorScheme = lightColorScheme
) {

    companion object {
        val Default: ThemeColor = ThemeColor("Default", lightColorScheme(), darkColorScheme())
    }

    fun colorScheme(isDarkTheme: Boolean): ColorScheme =
        if (isDarkTheme) darkColorScheme else lightColorScheme

    @Composable
    fun asAnimateColorScheme(
        isDarkTheme: Boolean,
        animationSpec: AnimationSpec<Color> = tween(600)
    ): ColorScheme = colorScheme(isDarkTheme).let {
        ColorScheme(
            primary = animateColorAsState(it.primary, animationSpec = animationSpec).value,
            onPrimary = animateColorAsState(
                it.onPrimary,
                animationSpec = animationSpec
            ).value,
            primaryContainer = animateColorAsState(
                it.primaryContainer,
                animationSpec = animationSpec
            ).value,
            onPrimaryContainer = animateColorAsState(
                it.onPrimaryContainer,
                animationSpec = animationSpec
            ).value,
            inversePrimary = animateColorAsState(
                it.inversePrimary,
                animationSpec = animationSpec
            ).value,
            secondary = animateColorAsState(
                it.secondary,
                animationSpec = animationSpec
            ).value,
            onSecondary = animateColorAsState(
                it.onSecondary,
                animationSpec = animationSpec
            ).value,
            secondaryContainer = animateColorAsState(
                it.secondaryContainer,
                animationSpec = animationSpec
            ).value,
            onSecondaryContainer = animateColorAsState(
                it.onSecondaryContainer,
                animationSpec = animationSpec
            ).value,
            tertiary = animateColorAsState(
                it.tertiary,
                animationSpec = animationSpec
            ).value,
            onTertiary = animateColorAsState(
                it.onTertiary,
                animationSpec = animationSpec
            ).value,
            tertiaryContainer = animateColorAsState(
                it.tertiaryContainer,
                animationSpec = animationSpec
            ).value,
            onTertiaryContainer = animateColorAsState(
                it.onTertiaryContainer,
                animationSpec = animationSpec
            ).value,
            background = animateColorAsState(
                it.background,
                animationSpec = animationSpec
            ).value,
            onBackground = animateColorAsState(
                it.onBackground,
                animationSpec = animationSpec
            ).value,
            surface = animateColorAsState(it.surface, animationSpec = animationSpec).value,
            onSurface = animateColorAsState(
                it.onSurface,
                animationSpec = animationSpec
            ).value,
            surfaceVariant = animateColorAsState(
                it.surfaceVariant,
                animationSpec = animationSpec
            ).value,
            onSurfaceVariant = animateColorAsState(
                it.onSurfaceVariant,
                animationSpec = animationSpec
            ).value,
            surfaceTint = animateColorAsState(
                it.surfaceTint,
                animationSpec = animationSpec
            ).value,
            inverseSurface = animateColorAsState(
                it.inverseSurface,
                animationSpec = animationSpec
            ).value,
            inverseOnSurface = animateColorAsState(
                it.inverseOnSurface,
                animationSpec = animationSpec
            ).value,
            error = animateColorAsState(it.error, animationSpec = animationSpec).value,
            onError = animateColorAsState(it.onError, animationSpec = animationSpec).value,
            errorContainer = animateColorAsState(
                it.errorContainer,
                animationSpec = animationSpec
            ).value,
            onErrorContainer = animateColorAsState(
                it.onErrorContainer,
                animationSpec = animationSpec
            ).value,
            outline = animateColorAsState(it.outline, animationSpec = animationSpec).value,
            outlineVariant = animateColorAsState(
                it.outlineVariant,
                animationSpec = animationSpec
            ).value,
            scrim = animateColorAsState(it.scrim, animationSpec = animationSpec).value,
            surfaceBright = animateColorAsState(
                it.surfaceBright,
                animationSpec = animationSpec
            ).value,
            surfaceDim = animateColorAsState(
                it.surfaceDim,
                animationSpec = animationSpec
            ).value,
            surfaceContainer = animateColorAsState(
                it.surfaceContainer,
                animationSpec = animationSpec
            ).value,
            surfaceContainerHigh = animateColorAsState(
                it.surfaceContainerHigh,
                animationSpec = animationSpec
            ).value,
            surfaceContainerHighest = animateColorAsState(
                it.surfaceContainerHighest,
                animationSpec = animationSpec
            ).value,
            surfaceContainerLow = animateColorAsState(
                it.surfaceContainerLow,
                animationSpec = animationSpec
            ).value,
            surfaceContainerLowest = animateColorAsState(
                it.surfaceContainerLowest,
                animationSpec = animationSpec
            ).value,
        )
    }

}

package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

val LocalAppColors = staticCompositionLocalOf { AppColors.Light }
val LocalAppTypography = staticCompositionLocalOf { AppTypography.default() }
val LocalAppMotion = staticCompositionLocalOf { AppMotion.Default }

/** 当前"前景色"（相当于 UIKit 的 label 色在容器内的继承），按钮 / 着色面会覆盖它。 */
val LocalContentColor = compositionLocalOf { Color(Palette.LightLabel) }

/** 当前默认文本样式（默认 body）。 */
val LocalTextStyle = compositionLocalOf { AppTypography.default().body }

/**
 * VRCM 设计系统入口：Apple HIG 而非 Material 3。
 * - [dark] 默认跟随系统；主题色（[accent]）来自设置里选的内置主题。
 * - 无障碍偏好从系统读取，玻璃 / 动效据此回退。
 * - 按压反馈是整块压暗 / 提亮（[PressHighlightIndication]），不是涟漪。
 * - [colorAnimationSpec] 非空时，切换深浅色 / 主题色会逐色过渡。
 */
@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    accent: AccentSpec = Accents.default,
    typography: AppTypography = AppTypography.default(),
    accessibility: AccessibilityPrefs = rememberSystemAccessibilityPrefs(),
    colorAnimationSpec: AnimationSpec<Float>? = null,
    content: @Composable () -> Unit,
) {
    val targetColors = remember(dark, accent) { if (dark) AppColors.dark(accent) else AppColors.light(accent) }
    val colors = if (colorAnimationSpec == null || accessibility.reduceMotion) {
        targetColors
    } else {
        animateAppColors(targetColors, colorAnimationSpec)
    }
    val motion = remember(accessibility.reduceMotion) {
        if (accessibility.reduceMotion) AppMotion.forReduced() else AppMotion.Default
    }
    val indication = remember(colors.isDark, accessibility.increaseContrast) {
        PressHighlightIndication.forTheme(colors.isDark, accessibility.increaseContrast)
    }
    val selectionColors = remember(colors.tint) {
        TextSelectionColors(handleColor = colors.tint, backgroundColor = colors.tint.copy(alpha = 0.3f))
    }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides typography,
        LocalAppMotion provides motion,
        LocalAccessibilityPrefs provides accessibility,
        LocalContentColor provides colors.label,
        LocalTextStyle provides typography.body,
        LocalIndication provides indication,
        LocalTextSelectionColors provides selectionColors,
        content = content,
    )
}

/** 便捷访问：`AppTheme.colors.tint`、`AppTheme.type.headline`。 */
object AppTheme {
    val colors: AppColors @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppTypography @Composable @ReadOnlyComposable get() = LocalAppTypography.current
    val motion: AppMotion @Composable @ReadOnlyComposable get() = LocalAppMotion.current
    val a11y: AccessibilityPrefs @Composable @ReadOnlyComposable get() = LocalAccessibilityPrefs.current
}

/** 在子树里换前景色（例如着色按钮内的字），可同时换默认文本样式。 */
@Composable
fun ProvideContentColor(color: Color, textStyle: TextStyle? = null, content: @Composable () -> Unit) {
    if (textStyle == null) {
        CompositionLocalProvider(LocalContentColor provides color, content = content)
    } else {
        CompositionLocalProvider(
            LocalContentColor provides color,
            LocalTextStyle provides LocalTextStyle.current.merge(textStyle),
            content = content,
        )
    }
}

/** 在子树里合并默认文本样式。 */
@Composable
fun ProvideTextStyle(value: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.merge(value), content = content)
}

@Composable
private fun animateAppColors(target: AppColors, animationSpec: AnimationSpec<Float>): AppColors {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        // 连续切换时从当前的中间色接着走，而不是跳回上一套令牌
        from = lerp(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec)
    }
    return if (progress.value >= 1f) to else lerp(from, to, progress.value)
}

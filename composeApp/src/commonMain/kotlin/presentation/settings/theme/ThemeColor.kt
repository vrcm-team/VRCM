package io.github.vrcmteam.vrcm.presentation.settings.theme

import io.github.vrcmteam.vrcm.presentation.designsystem.AccentSpec
import io.github.vrcmteam.vrcm.presentation.designsystem.Accents
import io.github.vrcmteam.vrcm.presentation.designsystem.AppColors
















/**
 * 设置里可选的主题色。[name] 是持久化在设置里的标识，[accent] 是它在设计系统里对应的强调色；
 * 背景、文字等其余语义色不随主题色变化。
 */
class ThemeColor(val accent: AccentSpec) {
    val name: String get() = accent.id

    /** 这个主题色在给定深浅模式下的整套令牌（设置页的主题色预览用）。 */
    fun colors(isDarkTheme: Boolean): AppColors =
        if (isDarkTheme) AppColors.dark(accent) else AppColors.light(accent)

    companion object {
        /** 设置里可选的全部主题色，顺序即色板上的顺序。 */
        val all: List<ThemeColor> = Accents.all.map(::ThemeColor)
        val Default: ThemeColor = all.first { it.accent == Accents.default }
    }
}

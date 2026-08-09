package io.github.vrcmteam.vrcm.presentation.adaptive

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class AppWindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

fun appWindowWidthClass(width: Dp): AppWindowWidthClass = when {
    width < 600.dp -> AppWindowWidthClass.Compact
    width < 840.dp -> AppWindowWidthClass.Medium
    else -> AppWindowWidthClass.Expanded
}

val LocalAppWindowWidthClass: ProvidableCompositionLocal<AppWindowWidthClass> =
    staticCompositionLocalOf { AppWindowWidthClass.Compact }

/**
 * 页面实际可用的内容区尺寸。Desktop 上自绘标题栏占据窗口顶部，
 * 因此它小于窗口尺寸；需要按"整页多大"计算版式的地方必须用它而不是窗口尺寸。
 */
val LocalAppContentSize: ProvidableCompositionLocal<DpSize> =
    staticCompositionLocalOf { DpSize.Zero }

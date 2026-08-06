package io.github.vrcmteam.vrcm.presentation.adaptive

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
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

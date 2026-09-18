package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled

@Composable
actual fun rememberSystemAccessibilityPrefs(): AccessibilityPrefs = remember {
    AccessibilityPrefs(
        reduceTransparency = UIAccessibilityIsReduceTransparencyEnabled(),
        reduceMotion = UIAccessibilityIsReduceMotionEnabled(),
        increaseContrast = UIAccessibilityDarkerSystemColorsEnabled(),
    )
}

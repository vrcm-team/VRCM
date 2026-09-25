package io.github.vrcmteam.vrcm.presentation.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 系统无障碍偏好：
 * Reduce Transparency → 玻璃回退为不透明；Increase Contrast → 加强填充与描边；Reduce Motion → 去掉形变 / 位移动画。
 */
@Immutable
data class AccessibilityPrefs(
    val reduceTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val increaseContrast: Boolean = false,
) {
    companion object {
        val None = AccessibilityPrefs()
    }
}

val LocalAccessibilityPrefs = staticCompositionLocalOf { AccessibilityPrefs.None }

/** 读取系统无障碍偏好；iOS 走 UIAccessibility，Android 走 Settings（无"降低透明度"，恒为 false），Desktop 无对应接口。 */
@Composable
expect fun rememberSystemAccessibilityPrefs(): AccessibilityPrefs

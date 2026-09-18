package io.github.vrcmteam.vrcm.presentation.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSystemAccessibilityPrefs(): AccessibilityPrefs {
    val context = LocalContext.current
    return remember(context) {
        val resolver = context.contentResolver
        val animatorScale = runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val highTextContrast = runCatching {
            Settings.Secure.getInt(resolver, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
        AccessibilityPrefs(
            reduceTransparency = false,
            reduceMotion = animatorScale == 0f,
            increaseContrast = highTextContrast,
        )
    }
}

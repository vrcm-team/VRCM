package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.runtime.Composable
import io.github.vrcmteam.vrcm.AppPlatform
import java.awt.Desktop
import java.net.URI

actual fun AppPlatform.openUrl(url: String) {
    Desktop.getDesktop().browse(URI(url))
}

actual val AppPlatform.isSupportBlur: Boolean
    get() = true

@Composable
actual fun AppPlatform.ChangeStatusBarDarkTheme(isDark: Boolean) {
}
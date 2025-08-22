package io.github.vrcmteam.vrcm.presentation.extensions

import androidx.compose.runtime.Composable
import io.github.vrcmteam.vrcm.AppPlatform
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun AppPlatform.openUrl(url: String) {
    UIApplication.sharedApplication.openURL(NSURL(string = url), emptyMap<Any?, Any>()) {
    }
}

actual val AppPlatform.isSupportBlur: Boolean
    get() = true

@Composable
actual fun AppPlatform.ChangeStatusBarDarkTheme(isDark: Boolean) {
}
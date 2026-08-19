package io.github.vrcmteam.vrcm.presentation.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberSystemInDarkTheme(): Boolean {
    val composeSystemDark = isSystemInDarkTheme()
    val viewController = LocalUIViewController.current
    var lastKnownDark by remember(viewController) {
        mutableStateOf(viewController.currentDarkTheme() ?: composeSystemDark)
    }
    val currentDark = viewController.currentDarkTheme()

    SideEffect {
        currentDark?.let { lastKnownDark = it }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewController.currentDarkTheme()?.let { lastKnownDark = it }
    }

    return currentDark ?: lastKnownDark
}

@Composable
actual fun ChangeStatusBarDarkTheme(isDark: Boolean) {
}

@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit = {
    onResult(true)
}

@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.currentDarkTheme(): Boolean? =
    view.window?.traitCollection?.userInterfaceStyle.asDarkThemeOrNull()
        ?: parentViewController?.traitCollection?.userInterfaceStyle.asDarkThemeOrNull()
        ?: traitCollection.userInterfaceStyle.asDarkThemeOrNull()

private fun UIUserInterfaceStyle?.asDarkThemeOrNull(): Boolean? = when (this) {
    UIUserInterfaceStyle.UIUserInterfaceStyleDark -> true
    UIUserInterfaceStyle.UIUserInterfaceStyleLight -> false
    else -> null
}

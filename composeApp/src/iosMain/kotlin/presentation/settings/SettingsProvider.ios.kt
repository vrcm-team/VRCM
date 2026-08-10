package io.github.vrcmteam.vrcm.presentation.settings

import androidx.compose.runtime.Composable

@Composable
actual fun ChangeStatusBarDarkTheme(isDark: Boolean) {
}
@Composable
actual fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit = {
    onResult(true)
}

package io.github.vrcmteam.vrcm.presentation.screens.gallery.editor

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun BlockBackNavigation(enabled: Boolean) {
    BackHandler(enabled = enabled) {}
}

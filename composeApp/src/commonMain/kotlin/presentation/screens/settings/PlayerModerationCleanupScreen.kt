package io.github.vrcmteam.vrcm.presentation.screens.settings

import androidx.compose.runtime.Composable
import io.github.vrcmteam.vrcm.presentation.navigation.AppDetailRoute
import kotlinx.serialization.Serializable

/**
 * Retained so a saved navigation stack from an earlier app version still restores into the
 * consolidated player management page.
 */
@Serializable
object PlayerModerationCleanupScreen : AppDetailRoute {
    @Composable
    override fun Content() {
        PlayerModerationScreenContent()
    }
}

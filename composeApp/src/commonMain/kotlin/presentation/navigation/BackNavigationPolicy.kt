package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
internal class BackNavigationPolicy {
    private val blockers = mutableSetOf<Any>()

    var isBackNavigationEnabled by mutableStateOf(true)
        private set

    fun setBlocked(blocker: Any, blocked: Boolean) {
        if (blocked) {
            blockers += blocker
        } else {
            blockers -= blocker
        }
        isBackNavigationEnabled = blockers.isEmpty()
    }
}

internal val LocalBackNavigationPolicy = staticCompositionLocalOf<BackNavigationPolicy> {
    error("BackNavigationPolicy is not provided")
}

@Composable
internal fun BlockBackNavigation(blocked: Boolean) {
    val policy = LocalBackNavigationPolicy.current
    val blocker = remember { Any() }

    DisposableEffect(policy, blocker, blocked) {
        policy.setBlocked(blocker, blocked)
        onDispose { policy.setBlocked(blocker, blocked = false) }
    }
}

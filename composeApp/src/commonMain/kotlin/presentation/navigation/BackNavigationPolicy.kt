package io.github.vrcmteam.vrcm.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
internal class BackNavigationPolicy {
    private val blockers = mutableSetOf<Any>()
    private val handlers = mutableMapOf<Any, () -> Unit>()

    var isBackNavigationEnabled by mutableStateOf(true)
        private set

    var hasBackHandler by mutableStateOf(false)
        private set

    fun setBlocked(blocker: Any, blocked: Boolean) {
        if (blocked) {
            blockers += blocker
        } else {
            blockers -= blocker
        }
        isBackNavigationEnabled = blockers.isEmpty()
    }

    fun setBackHandler(owner: Any, handler: (() -> Unit)?) {
        if (handler == null) {
            handlers -= owner
        } else {
            handlers[owner] = handler
        }
        hasBackHandler = handlers.isNotEmpty()
    }

    fun shouldHandleBack(canNavigateBack: Boolean): Boolean =
        hasBackHandler || !isBackNavigationEnabled || canNavigateBack

    fun handleBack(canNavigateBack: Boolean, navigateBack: () -> Unit): Boolean {
        handlers.values.lastOrNull()?.let { handler ->
            handler()
            return true
        }
        if (!isBackNavigationEnabled) return true
        if (!canNavigateBack) return false

        navigateBack()
        return true
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

@Composable
internal fun HandleBackNavigation(enabled: Boolean, onBack: () -> Unit) {
    val policy = LocalBackNavigationPolicy.current
    val owner = remember { Any() }
    val currentOnBack by rememberUpdatedState(onBack)

    DisposableEffect(policy, owner, enabled) {
        policy.setBackHandler(owner, if (enabled) ({ currentOnBack() }) else null)
        onDispose { policy.setBackHandler(owner, handler = null) }
    }
}

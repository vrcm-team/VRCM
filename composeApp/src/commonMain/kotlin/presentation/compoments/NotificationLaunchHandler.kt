package io.github.vrcmteam.vrcm.presentation.compoments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.vrcmteam.vrcm.presentation.navigation.AppNavigator
import io.github.vrcmteam.vrcm.presentation.screens.home.HomeScreen
import io.github.vrcmteam.vrcm.presentation.screens.notification.NotificationScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.UserProfileScreen
import io.github.vrcmteam.vrcm.presentation.screens.user.data.UserProfileVo
import io.github.vrcmteam.vrcm.service.NotificationLaunchDestination
import io.github.vrcmteam.vrcm.service.NotificationLaunchInbox

@Composable
internal fun NotificationLaunchHandler(
    navigator: AppNavigator,
    inbox: NotificationLaunchInbox,
) {
    val request by inbox.pendingRequest.collectAsState()
    val isAuthenticated = navigator.items.any { it is HomeScreen }

    LaunchedEffect(request?.requestId, isAuthenticated) {
        val pending = request ?: return@LaunchedEffect
        if (!isAuthenticated) return@LaunchedEffect
        val route = when (pending.destination) {
            NotificationLaunchDestination.UserProfile ->
                UserProfileScreen(UserProfileVo(id = pending.targetId))
            NotificationLaunchDestination.NotificationCenter ->
                NotificationScreen(targetNotificationId = pending.targetId)
        }
        if (navigator.lastItem is NotificationScreen && route is NotificationScreen) {
            navigator replace route
        } else {
            navigator push route
        }
        inbox.consume(pending)
    }
}

package io.github.vrcmteam.vrcm

import android.content.Intent
import io.github.vrcmteam.vrcm.service.NotificationLaunchDestination
import io.github.vrcmteam.vrcm.service.NotificationLaunchInbox

internal const val ACTION_OPEN_NOTIFICATION_TARGET =
    "io.github.vrcmteam.vrcm.action.OPEN_NOTIFICATION_TARGET"
internal const val EXTRA_NOTIFICATION_DESTINATION = "notification-destination"
internal const val EXTRA_NOTIFICATION_TARGET_ID = "notification-target-id"

internal fun Intent.submitNotificationLaunch(inbox: NotificationLaunchInbox) {
    if (action != ACTION_OPEN_NOTIFICATION_TARGET) return
    val destination = getStringExtra(EXTRA_NOTIFICATION_DESTINATION)
        ?.let { value -> NotificationLaunchDestination.entries.firstOrNull { it.name == value } }
        ?: return
    val targetId = getStringExtra(EXTRA_NOTIFICATION_TARGET_ID) ?: return
    inbox.submit(destination, targetId)
}

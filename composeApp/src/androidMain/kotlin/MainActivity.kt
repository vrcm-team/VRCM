package io.github.vrcmteam.vrcm

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vrcmteam.vrcm.service.OfficialLinkInbox
import io.github.vrcmteam.vrcm.service.NotificationLaunchDestination
import io.github.vrcmteam.vrcm.service.NotificationLaunchInbox

class MainActivity : ComponentActivity() {
    private val officialLinkInbox = OfficialLinkInbox()
    private val notificationLaunchInbox = NotificationLaunchInbox()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            submitOfficialLink(intent)
            intent.submitNotificationLaunch(notificationLaunchInbox)
        } else {
            savedInstanceState.getString(PENDING_OFFICIAL_LINK_KEY)?.let(officialLinkInbox::submit)
            val destination = savedInstanceState.getString(PENDING_NOTIFICATION_DESTINATION_KEY)
                ?.let { value -> NotificationLaunchDestination.entries.firstOrNull { it.name == value } }
            val targetId = savedInstanceState.getString(PENDING_NOTIFICATION_TARGET_ID_KEY)
            if (destination != null && targetId != null) {
                notificationLaunchInbox.submit(destination, targetId)
            }
        }
        setContent {
            App(
                officialLinkInbox = officialLinkInbox,
                notificationLaunchInbox = notificationLaunchInbox,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        submitOfficialLink(intent)
        intent.submitNotificationLaunch(notificationLaunchInbox)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        officialLinkInbox.pendingUrl()?.let {
            outState.putString(PENDING_OFFICIAL_LINK_KEY, it)
        }
        notificationLaunchInbox.pendingRequest.value?.let { request ->
            outState.putString(PENDING_NOTIFICATION_DESTINATION_KEY, request.destination.name)
            outState.putString(PENDING_NOTIFICATION_TARGET_ID_KEY, request.targetId)
        }
        super.onSaveInstanceState(outState)
    }

    private fun submitOfficialLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(officialLinkInbox::submit)
    }

    private companion object {
        const val PENDING_OFFICIAL_LINK_KEY = "pending-official-link"
        const val PENDING_NOTIFICATION_DESTINATION_KEY = "pending-notification-destination"
        const val PENDING_NOTIFICATION_TARGET_ID_KEY = "pending-notification-target-id"
    }
}

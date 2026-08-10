package io.github.vrcmteam.vrcm

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.vrcmteam.vrcm.service.OfficialLinkInbox

class MainActivity : ComponentActivity() {
    private val officialLinkInbox = OfficialLinkInbox()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            submitOfficialLink(intent)
        } else {
            savedInstanceState.getString(PENDING_OFFICIAL_LINK_KEY)?.let(officialLinkInbox::submit)
        }
        setContent {
            App(
                isConfigurationChange = { isChangingConfigurations },
                officialLinkInbox = officialLinkInbox,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        submitOfficialLink(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        officialLinkInbox.pendingUrl()?.let {
            outState.putString(PENDING_OFFICIAL_LINK_KEY, it)
        }
        super.onSaveInstanceState(outState)
    }

    private fun submitOfficialLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(officialLinkInbox::submit)
    }

    private companion object {
        const val PENDING_OFFICIAL_LINK_KEY = "pending-official-link"
    }
}

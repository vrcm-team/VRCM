package io.github.vrcmteam.vrcm

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Moves externally opened official links into VRCM's own task. */
class OfficialLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.takeIf { it.action == Intent.ACTION_VIEW }?.data?.let { officialLink ->
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = officialLink
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }
        finish()
    }
}

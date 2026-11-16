package io.github.vrcmteam.vrcm

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Receives the launcher's task-reset flags outside the app task, then reuses its MainActivity. */
class ScanShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == ACTION_SCAN_QR_CODE) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = ACTION_SCAN_QR_CODE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
        }
        finish()
    }

    companion object {
        internal const val ACTION_SCAN_QR_CODE = "io.github.vrcmteam.vrcm.SCAN_QR_CODE"
    }
}

package io.github.vrcmteam.vrcm.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

/** Keeps background monitoring work scheduled for exactly the foreground-service lifetime. */
@SuppressLint("WakelockTimeout")
internal class BackgroundNetworkWakeLock(context: Context) : AutoCloseable {
    private val wakeLock = context.getSystemService(PowerManager::class.java).newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "${context.packageName}:friend-activity-monitor",
    ).apply {
        setReferenceCounted(false)
        acquire()
    }

    val isHeld: Boolean
        get() = wakeLock.isHeld

    override fun close() {
        if (wakeLock.isHeld) wakeLock.release()
    }
}

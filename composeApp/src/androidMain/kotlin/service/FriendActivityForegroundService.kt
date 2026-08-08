package io.github.vrcmteam.vrcm.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.websocket.WebSocketApi
import io.github.vrcmteam.vrcm.presentation.notifications.FriendNotificationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * User-enabled Android foreground monitor.
 *
 * The WebSocket is the real-time source. The periodic refresh is deliberately
 * sparse and only repairs a missed socket event, so it does not poll the full
 * friend list every few minutes while the phone is idle.
 */
class FriendActivityForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        startForeground(MONITOR_ID, FriendNotificationFactory(this).monitoringNotification())
        val koin = GlobalContext.get()
        koin.get<WebSocketApi>().setBackgroundMonitoringEnabled(true)
        val friendService = koin.get<FriendService>()
        scope.launch {
            SharedFlowCentre.currentSession.collect { session ->
                if (session != null) koin.get<FriendOnlineNotificationService>()
            }
        }
        scope.launch {
            while (isActive) {
                delay(FALLBACK_REFRESH_INTERVAL_MILLIS)
                if (SharedFlowCentre.currentSession.value != null) friendService.refreshFriendList()
            }
        }
        scope.launch { SharedFlowCentre.logout.collect { stopSelf() } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        GlobalContext.getOrNull()?.get<WebSocketApi>()?.setBackgroundMonitoringEnabled(false)
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private companion object {
        const val MONITOR_ID = 0x5652434d
        const val FALLBACK_REFRESH_INTERVAL_MILLIS = 15 * 60 * 1_000L
    }
}

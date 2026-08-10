package io.github.vrcmteam.vrcm.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.websocket.WebSocketApi
import io.github.vrcmteam.vrcm.presentation.notifications.FriendNotificationFactory
import org.koin.core.logger.Logger
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
 * The WebSocket is the real-time source. The periodic refresh is a fallback
 * and only runs while the socket is disconnected.
 */
class FriendActivityForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        startForeground(MONITOR_ID, FriendNotificationFactory(this).monitoringNotification())
        val koin = GlobalContext.get()
        koin.get<WebSocketApi>().setBackgroundMonitoringEnabled(true)
        val friendService = koin.get<FriendService>()
        koin.get<FriendActivityService>()
        val webSocketApi = koin.get<WebSocketApi>()
        val logger = koin.get<Logger>()
        scope.launch {
            while (isActive) {
                delay(FALLBACK_REFRESH_INTERVAL_MILLIS)
                if (SharedFlowCentre.currentSession.value != null && !webSocketApi.isConnected()) {
                    runCatching { friendService.refreshFriendList() }
                        .onFailure { logger.warn("Background friend refresh failed: ${it.message.orEmpty()}") }
                }
            }
        }
        scope.launch { SharedFlowCentre.logout.collect { stopSelf() } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        GlobalContext.getOrNull()?.let { koin ->
            val webSocketApi = koin.get<WebSocketApi>()
            webSocketApi.setBackgroundMonitoringEnabled(false)
            if (!webSocketApi.isAppForeground()) {
                koin.get<FriendActivityService>().onAppStopped()
            }
        }
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

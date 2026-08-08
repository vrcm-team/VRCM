package io.github.vrcmteam.vrcm

import android.app.Application
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.modules.platformModule
import io.github.vrcmteam.vrcm.service.FriendOnlineNotificationService
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Logger

class VRCMApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val app = startKoin {
            androidLogger()
            androidContext(this@VRCMApplication)
            modules(commonModules + platformModule)
        }
        val koin = app.koin
        koin.get<FriendOnlineNotificationService>().start()

        val settingsDao = koin.get<SettingsDao>()
        if (!settingsDao.backgroundFriendMonitoringEnabled) return
        // The switch is persisted, so the monitor has to come back after a restart; otherwise the
        // settings page keeps showing "on" while nothing runs. Waiting for a session avoids both an
        // idle monitor notification before login and the foreground-service start restriction that
        // applies when Application.onCreate runs in the background.
        scope.launch {
            SharedFlowCentre.currentSession.filterNotNull().first()
            if (!settingsDao.backgroundFriendMonitoringEnabled) return@launch
            val result = koin.get<AppPlatform>().setBackgroundFriendMonitoringEnabled(true)
            if (result == BackgroundFriendMonitoringResult.Started) return@launch
            // Keep the persisted switch honest instead of claiming monitoring is active.
            settingsDao.backgroundFriendMonitoringEnabled = false
            koin.get<Logger>().error("Background friend monitoring could not be restored: $result")
        }
    }
}

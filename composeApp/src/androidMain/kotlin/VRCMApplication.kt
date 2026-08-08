package io.github.vrcmteam.vrcm

import android.app.Application
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.modules.platformModule
import io.github.vrcmteam.vrcm.presentation.settings.SettingsModel
import io.github.vrcmteam.vrcm.service.FriendOnlineNotificationService
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
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
        // Every session counts, not just the first one: the foreground service stops itself on
        // logout, so signing back in has to bring it up again. Waiting for a session also avoids an
        // idle monitor notification before login and the foreground-service start restriction that
        // applies while Application.onCreate runs in the background. Starting an already running
        // service only reaches onStartCommand, so repeating the call is safe.
        scope.launch {
            SharedFlowCentre.currentSession.filterNotNull().collect {
                if (!settingsDao.backgroundFriendMonitoringEnabled) return@collect
                val result = koin.get<AppPlatform>().setBackgroundFriendMonitoringEnabled(true)
                if (result == BackgroundFriendMonitoringResult.Started) return@collect
                // Report instead of writing the DAO directly, so the settings state stays the single
                // source of truth and the switch stops claiming monitoring is active.
                koin.get<SettingsModel>().reportBackgroundMonitoringUnavailable()
                koin.get<Logger>().error("Background friend monitoring could not be restored: $result")
            }
        }
    }
}

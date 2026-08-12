package io.github.vrcmteam.vrcm

import android.app.Application
import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.di.commonModules
import io.github.vrcmteam.vrcm.di.modules.platformModule
import io.github.vrcmteam.vrcm.presentation.settings.SettingsModel
import io.github.vrcmteam.vrcm.service.FriendOnlineNotificationService
import io.github.vrcmteam.vrcm.service.VrchatStatusNotificationService
import io.github.vrcmteam.vrcm.storage.SettingsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Logger

class VRCMApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val app = startKoin {
            androidLogger()
            androidContext(this@VRCMApplication)
            modules(commonModules + platformModule)
        }
        val koin = app.koin

        // Statuspage is public and independent of VRChat authentication. The application owns the
        // polling lifetime; Android's optional foreground monitor keeps this process alive when the
        // user also wants reliable background delivery.
        scope.launch {
            val statusNotifications = koin.get<VrchatStatusNotificationService>()
            while (isActive) {
                statusNotifications.checkOnce()
                delay(STATUS_REFRESH_INTERVAL_MILLIS)
            }
        }

        // 好友通知与后台监测都只在登录之后才有意义，所以整条依赖链等出现有效会话再解析。
        // 放在 onCreate 里同步 get 会把 Room、网络客户端、通知渠道以及 AccountCacheManager
        // 的旧缓存清理全部拽到启动的主线程上：既拖慢冷启动，任何一环抛异常都会让应用一打开就崩，
        // 而不是只让通知功能不可用。start() 与前台服务启动都是幂等的，重复登录不会重复初始化。
        scope.launch {
            SharedFlowCentre.currentSession.filterNotNull().collect {
                koin.get<FriendOnlineNotificationService>().start()

                val settingsDao = koin.get<SettingsDao>()
                if (!settingsDao.backgroundFriendMonitoringEnabled) return@collect
                // 前台服务收到 logout 会自行停止，所以每次重新登录都要再拉起一次。
                val result = koin.get<AppPlatform>().setBackgroundFriendMonitoringEnabled(true)
                if (result == BackgroundFriendMonitoringResult.Started) return@collect
                // 通过设置状态纠正开关，而不是直接写 DAO，避免绕开界面的单一数据源。
                koin.get<SettingsModel>().reportBackgroundMonitoringUnavailable()
                koin.get<Logger>().error("Background friend monitoring could not be restored: $result")
            }
        }
    }

    private companion object {
        const val STATUS_REFRESH_INTERVAL_MILLIS = 5 * 60_000L
    }
}

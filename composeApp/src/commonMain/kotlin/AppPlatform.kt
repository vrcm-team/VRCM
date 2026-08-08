package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import org.koin.compose.getKoin
import org.koin.core.component.KoinComponent

interface AppPlatform : KoinComponent {
    val name: String
    val version: String
    val type: AppPlatformType

    val supportsBackgroundFriendMonitoring: Boolean get() = false
    fun hasBackgroundFriendMonitoringPermission(): Boolean = true
    fun requestBackgroundFriendMonitoringPermission() = Unit
    fun openNotificationSettings() = Unit
    fun setBackgroundFriendMonitoringEnabled(enabled: Boolean): BackgroundFriendMonitoringResult =
        BackgroundFriendMonitoringResult.Unsupported
    val supportsBatteryOptimizationSettings: Boolean get() = false
    fun isIgnoringBatteryOptimizations(): Boolean = true
    fun openBatteryOptimizationSettings() = Unit
}

enum class BackgroundFriendMonitoringResult { Started, Stopped, PermissionRequired, Unsupported }

enum class AppPlatformType { Android, Desktop, Ios, Web }

@Composable
fun getAppPlatform(): AppPlatform = getKoin().get()

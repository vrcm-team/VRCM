package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import org.koin.compose.getKoin
import org.koin.core.component.KoinComponent

interface AppPlatform : KoinComponent {
    val name: String
    val version: String
    val type: AppPlatformType

    val supportsFriendActivityNotifications: Boolean get() = false
    val supportsBackgroundFriendMonitoring: Boolean get() = false
    fun hasBackgroundFriendMonitoringPermission(): Boolean = true
    fun openNotificationSettings() = Unit
    fun openAppSettings() = Unit
    fun setBackgroundFriendMonitoringEnabled(enabled: Boolean): BackgroundFriendMonitoringResult =
        BackgroundFriendMonitoringResult.Unsupported

    /** Resets the timer only when an existing platform monitor can consume the request. */
    fun resetBackgroundFriendMonitoringTimer() = Unit
    val supportsBatteryOptimizationSettings: Boolean get() = false
    fun isIgnoringBatteryOptimizations(): Boolean = true
    fun openBatteryOptimizationSettings() = Unit

    suspend fun installAppUpdate(
        tagName: String,
        downloadUrls: List<String>,
        onProgress: (Float?) -> Unit,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("In-app updates are not supported on $name"))
}

enum class BackgroundFriendMonitoringResult { Started, Stopped, PermissionRequired, Unsupported }

enum class AppPlatformType { Android, Desktop, Ios, Web }

@Composable
fun getAppPlatform(): AppPlatform = getKoin().get()

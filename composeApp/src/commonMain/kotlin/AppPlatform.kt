package io.github.vrcmteam.vrcm

import androidx.compose.runtime.Composable
import org.koin.compose.getKoin
import org.koin.core.component.KoinComponent

interface AppPlatform : KoinComponent {
    val name: String
    val version: String
    val type: AppPlatformType

    /** 应用内检查新版本的来源，由安装包的分发渠道决定；null 表示不在应用内检查。 */
    val updateSource: AppUpdateSource? get() = AppUpdateSource.GitHub

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

/** 新版本从哪里查、"更新"跳去哪里。 */
sealed interface AppUpdateSource {
    /** GitHub Release 分发的安装包：查询仓库最新 Release。 */
    data object GitHub : AppUpdateSource

    /**
     * App Store 分发的安装包：按 [bundleId] 查询商店上架版本，更新只跳转 App Store 商品页。
     *
     * @param region 设备地区（ISO 3166-1 两位码），优先查询该地区的商店
     */
    data class AppStore(val bundleId: String, val region: String?) : AppUpdateSource
}

enum class BackgroundFriendMonitoringResult { Started, Stopped, PermissionRequired, Unsupported }

enum class AppPlatformType { Android, Desktop, Ios, Web }

@Composable
fun getAppPlatform(): AppPlatform = getKoin().get()

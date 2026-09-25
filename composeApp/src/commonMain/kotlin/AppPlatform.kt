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

    /** 在应用内下载并安装新版本；null 表示"更新"只打开版本页面或商店商品页。 */
    val appUpdateInstaller: AppUpdateInstaller? get() = null

    /** 是否提供 VRChat 奖励兑换码入口；App Store 版按审核准则 3.1.1 不提供应用内兑换码。 */
    val supportsRewardCodeRedemption: Boolean get() = true

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
}

/** 应用内更新：下载新版本的安装包，再交给系统安装器覆盖安装。 */
interface AppUpdateInstaller {
    /**
     * 下载新版本的安装包并校验；某个下载源连不上、中断或给的不是本应用的安装包时换下一个源。
     *
     * @param downloadUrls 新版本的全部附件地址，由实现挑出本平台的安装包
     * @param onProgress 已下载的字节数和总字节数；下载源没给出大小时总数为 null
     */
    suspend fun download(
        tagName: String,
        downloadUrls: List<String>,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<DownloadedAppUpdate>

    /** 打开系统安装器安装 [update]；用户在安装器里取消后可以再次调用。 */
    fun install(update: DownloadedAppUpdate): Result<Unit>

    /** 删掉用不上的安装包，例如已经装上的版本；比当前版本新、还没安装的会保留。启动时在后台调用。 */
    fun deleteObsoletePackages()
}

/** 下载好并校验通过的新版本安装包，[path] 是它在本机的位置。 */
class DownloadedAppUpdate(val path: String)

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

    /**
     * Google Play 分发的安装包：通过 Play 应用内更新接口查询商店上的新版本，更新只跳转 Play 商品页。
     * Play 只提供可更新到的 versionCode，不提供版本名和更新说明。
     *
     * @param packageName 应用包名，用来拼 Play 商品页地址
     * @param availableVersionCode 查询 Play 上可更新到的 versionCode，没有新版本时为 null
     */
    class GooglePlay(
        val packageName: String,
        val availableVersionCode: suspend () -> Result<Int?>,
    ) : AppUpdateSource
}

enum class BackgroundFriendMonitoringResult { Started, Stopped, PermissionRequired, Unsupported }

enum class AppPlatformType { Android, Desktop, Ios, Web }

@Composable
fun getAppPlatform(): AppPlatform = getKoin().get()

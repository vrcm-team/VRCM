package io.github.vrcmteam.vrcm

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.CancellationException

// Google Play 分发的包：Play 政策只允许通过 Play 更新。新版本用 Play 应用内更新接口查询，"更新"只跳转 Play 商品页。

internal fun AndroidAppPlatform.distributionUpdateSource(): AppUpdateSource =
    AppUpdateSource.GooglePlay(packageName = context.packageName) { availablePlayVersionCode(context) }

internal fun AndroidAppPlatform.distributionUpdateInstaller(): AppUpdateInstaller? = null

/**
 * Play 上有可更新的版本时返回它的 versionCode。
 * 不是从 Play 安装的包（本地调试、侧载）或设备没有 Play 商店时查不到，视为没有更新。
 */
private suspend fun availablePlayVersionCode(context: Context): Result<Int?> = try {
    val info = AppUpdateManagerFactory.create(context).requestAppUpdateInfo()
    Result.success(info.availableVersionCode().takeIf { info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE })
} catch (cause: CancellationException) {
    throw cause
} catch (cause: InstallException) {
    if (cause.errorCode in notInstalledFromPlayErrors) Result.success(null) else Result.failure(cause)
} catch (cause: Exception) {
    Result.failure(cause)
}

private val notInstalledFromPlayErrors = setOf(
    InstallErrorCode.ERROR_APP_NOT_OWNED,
    InstallErrorCode.ERROR_API_NOT_AVAILABLE,
    InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND,
)

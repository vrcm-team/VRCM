package io.github.vrcmteam.vrcm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.github.vrcmteam.vrcm.service.FriendActivityForegroundService
import org.koin.core.logger.Logger

class AndroidAppPlatform(val context: Context, private val logger: Logger) : AppPlatform {
    override val name = "Android"
    override val version = Build.VERSION.SDK_INT.toString()
    override val type = AppPlatformType.Android
    override val supportsFriendActivityNotifications = true
    override val supportsBackgroundFriendMonitoring = true

    override fun hasBackgroundFriendMonitoringPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun setBackgroundFriendMonitoringEnabled(enabled: Boolean): BackgroundFriendMonitoringResult {
        val intent = Intent(context, FriendActivityForegroundService::class.java)
        if (!enabled) { context.stopService(intent); return BackgroundFriendMonitoringResult.Stopped }
        if (!hasBackgroundFriendMonitoringPermission()) return BackgroundFriendMonitoringResult.PermissionRequired
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            BackgroundFriendMonitoringResult.Started
        }.getOrElse {
            logger.error("Failed to start background friend monitoring: ${it.message.orEmpty()}")
            BackgroundFriendMonitoringResult.Unsupported
        }
    }

    override fun resetBackgroundFriendMonitoringTimer() {
        val intent = Intent(FriendActivityForegroundService.ACTION_RESET_MONITORING_TIMER)
            .setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    override val supportsBatteryOptimizationSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    override fun isIgnoringBatteryOptimizations(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    override fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        // Some vendor ROMs ship without this settings screen, so a missing activity must not crash.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { logger.error("Failed to open battery optimization settings: ${it.message.orEmpty()}") }
    }

    override suspend fun installAppUpdate(
        tagName: String,
        downloadUrls: List<String>,
        onProgress: (Float?) -> Unit,
    ): Result<Unit> = downloadAndInstallAppUpdate(context, tagName, downloadUrls, onProgress)
}

package io.github.vrcmteam.vrcm

// GitHub Release 分发的 APK：查询 GitHub 新版本，并在应用内下载、安装新 APK。

internal fun AndroidAppPlatform.distributionUpdateSource(): AppUpdateSource = AppUpdateSource.GitHub

internal suspend fun AndroidAppPlatform.installDistributionUpdate(
    tagName: String,
    downloadUrls: List<String>,
    onProgress: (Float?) -> Unit,
): Result<Unit> = downloadAndInstallAppUpdate(context, tagName, downloadUrls, onProgress)

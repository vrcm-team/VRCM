package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.AppUpdateSource
import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.network.api.appstore.AppStoreApi
import io.github.vrcmteam.vrcm.network.api.appstore.data.AppStoreAppData
import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.service.data.VersionDto
import io.github.vrcmteam.vrcm.storage.SettingsDao

class VersionService(
    private val gitHubApi: GitHubApi,
    private val appStoreApi: AppStoreApi,
    private val settingsDao: SettingsDao,
) {

    /**
     * 从安装包的分发渠道获取最新版本信息。
     *
     * @param checkRemember 是否忽略用户已记住的版本
     */
    suspend fun checkVersion(source: AppUpdateSource, checkRemember: Boolean): Result<VersionDto> =
        when (source) {
            AppUpdateSource.GitHub -> latestGitHubRelease(checkRemember)
            is AppUpdateSource.AppStore -> latestAppStoreRelease(source, checkRemember)
            is AppUpdateSource.GooglePlay -> latestGooglePlayRelease(source, checkRemember)
        }

    fun rememberVersion(version: String?) {
        settingsDao.rememberVersion = version
    }

    /** GitHub 直连失败时会依次尝试镜像。 */
    private suspend fun latestGitHubRelease(checkRemember: Boolean): Result<VersionDto> {
        var lastFailure: Throwable? = null
        for (releaseUrl in releaseUrls) {
            val result = gitHubApi.latestRelease(releaseUrl)
            val releaseData = result.getOrNull()
            if (releaseData == null) {
                lastFailure = result.exceptionOrNull()
                continue
            }

            return Result.success(
                VersionDto(
                    tagName = releaseData.tagName,
                    htmlUrl = releaseData.htmlUrl,
                    body = releaseData.body,
                    hasNewVersion = hasNewVersion(releaseData.tagName, checkRemember),
                    downloadUrl = releaseData.assets.map { it.browserDownloadUrl },
                ),
            )
        }
        return Result.failure(lastFailure ?: IllegalStateException("Unable to reach GitHub release service"))
    }

    private suspend fun latestAppStoreRelease(
        source: AppUpdateSource.AppStore,
        checkRemember: Boolean,
    ): Result<VersionDto> = lookupAppStore(source).map { app ->
        if (app == null) {
            // 尚未上架（审核中、只在 TestFlight）时查不到结果，视为没有可更新的版本
            VersionDto(tagName = AppConst.APP_VERSION, htmlUrl = "", body = "", hasNewVersion = false)
        } else {
            VersionDto(
                tagName = app.version,
                // itms-apps 直接打开 App Store 应用，按用户 Apple ID 所在的商店展示商品页
                htmlUrl = "itms-apps://apps.apple.com/app/id${app.trackId}",
                body = app.releaseNotes,
                hasNewVersion = hasNewVersion(app.version, checkRemember),
            )
        }
    }

    /** 优先查设备地区的商店，发布说明是当地语言；该地区没上架或地区码不被商店接受时再查默认商店。 */
    private suspend fun lookupAppStore(source: AppUpdateSource.AppStore): Result<AppStoreAppData?> {
        source.region?.let { region ->
            appStoreApi.lookup(source.bundleId, region).getOrNull()?.results?.firstOrNull()
                ?.let { return Result.success(it) }
        }
        return appStoreApi.lookup(source.bundleId, country = null).map { it.results.firstOrNull() }
    }

    /**
     * Play 只在商店版本的 versionCode 比已安装的大时才报告有更新，所以不用再比较版本号；
     * 它不提供版本名和更新说明，"不再提示"按 versionCode 记，更新内容由商品页展示。
     */
    private suspend fun latestGooglePlayRelease(
        source: AppUpdateSource.GooglePlay,
        checkRemember: Boolean,
    ): Result<VersionDto> = source.availableVersionCode().map { versionCode ->
        if (versionCode == null) {
            VersionDto(tagName = AppConst.APP_VERSION, htmlUrl = "", body = "", hasNewVersion = false)
        } else {
            val versionTag = versionCode.toString()
            VersionDto(
                tagName = versionTag,
                // 用 https 商品页而不是 market://：装了 Play 商店会直接打开商店，没有商店的设备也不会因为无处理方而崩溃
                htmlUrl = "https://play.google.com/store/apps/details?id=${source.packageName}",
                body = "",
                hasNewVersion = !checkRemember || settingsDao.rememberVersion != versionTag,
                versionName = null,
            )
        }
    }

    /** 只提示比当前更新的版本：开发版、TestFlight 版可能比已发布的版本还新。 */
    private fun hasNewVersion(latestVersion: String, checkRemember: Boolean): Boolean =
        isNewerVersion(latestVersion, AppConst.APP_VERSION) &&
            (!checkRemember || settingsDao.rememberVersion != latestVersion)
}

/** 逐段按数字比较版本号：1.10.0 比 1.9.0 新，1.2 与 1.2.0 相同。 */
internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateParts = candidate.versionParts()
    val currentParts = current.versionParts()
    for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private fun String.versionParts(): List<Int> =
    trim().trimStart('v', 'V').split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

private val releaseUrls = listOf(
    AppConst.APP_GITHUB_LATEST_RELEASE_URL,
    "https://ghfast.top/${AppConst.APP_GITHUB_LATEST_RELEASE_URL}",
    "https://gh-proxy.com/${AppConst.APP_GITHUB_LATEST_RELEASE_URL}",
)

package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.service.data.VersionDto
import io.github.vrcmteam.vrcm.storage.SettingsDao

class VersionService(
    private val gitHubApi: GitHubApi,
    private val settingsDao: SettingsDao,
) {

    /**
     * 获取最新版本信息。GitHub 直连失败时会依次尝试镜像。
     *
     * @param checkRemember 是否忽略用户已记住的版本
     */
    suspend fun checkVersion(checkRemember: Boolean): Result<VersionDto> {
        var lastFailure: Throwable? = null
        for (releaseUrl in releaseUrls) {
            val result = gitHubApi.latestRelease(releaseUrl)
            val releaseData = result.getOrNull()
            if (releaseData == null) {
                lastFailure = result.exceptionOrNull()
                continue
            }

            val tagName = releaseData.tagName
            val hasNewVersion = AppConst.APP_VERSION != tagName &&
                (!checkRemember || settingsDao.rememberVersion != tagName)
            return Result.success(
                VersionDto(
                    tagName = tagName,
                    htmlUrl = releaseData.htmlUrl,
                    body = releaseData.body,
                    hasNewVersion = hasNewVersion,
                    downloadUrl = releaseData.assets.map { it.browserDownloadUrl },
                ),
            )
        }
        return Result.failure(lastFailure ?: IllegalStateException("Unable to reach GitHub release service"))
    }

    fun rememberVersion(version: String?) {
        settingsDao.rememberVersion = version
    }
}

private val releaseUrls = listOf(
    AppConst.APP_GITHUB_LATEST_RELEASE_URL,
    "https://ghfast.top/${AppConst.APP_GITHUB_LATEST_RELEASE_URL}",
    "https://gh-proxy.com/${AppConst.APP_GITHUB_LATEST_RELEASE_URL}",
)

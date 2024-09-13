package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.core.shared.AppConst
import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.service.data.VersionDto
import io.github.vrcmteam.vrcm.storage.SettingsDao

class VersionService(
    private val gitHubApi: GitHubApi,
    private val settingsDao: SettingsDao
) {

    /**
     * 获取最新的版本号,和版本链接
     * @param checkRemember 是否检查记住版本
     * @return 最新版本号和最新版本链接
     */
    suspend fun checkVersion(checkRemember: Boolean): Result<VersionDto> =
        gitHubApi.latestRelease(AppConst.APP_GITHUB_LATEST_RELEASE_URL).let {
            when {
                it.isSuccess -> {
                    val releaseData = it.getOrNull()!!
                    val tagName = releaseData.tagName
                    if (AppConst.APP_VERSION == tagName
                        || (checkRemember && settingsDao.rememberVersion == tagName)
                    ) {
                        // 当前版本是最新版本
                        Result.success(VersionDto(tagName, releaseData.htmlUrl, releaseData.body, false))
                    } else {
                        // 当前版本不是最新版本
                        Result.success(VersionDto(tagName, releaseData.htmlUrl, releaseData.body, true))
                    }
                }

                else -> Result.failure(it.exceptionOrNull()!!)
            }
        }

    fun rememberVersion(version: String?) {
        settingsDao.rememberVersion = version
    }

}
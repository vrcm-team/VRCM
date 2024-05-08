package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.service.data.VersionDto
import io.github.vrcmteam.vrcm.storage.SettingsDao

class VersionService(
    private val gitHubApi: GitHubApi,
    private val settingsDao: SettingsDao
) {
    companion object {
        private const val CURRENT_VERSION = "1.0.0"
    }


    /**
     * 获取最新的版本号,和版本链接
     * @param checkRemember 是否检查记住版本
     * @return 最新版本号和最新版本链接
     */
    suspend fun checkVersion(checkRemember: Boolean): Result<VersionDto> = gitHubApi.latestRelease().let {
        when {
            it.isSuccess -> {
                val releaseData = it.getOrNull()!!
                val tagName = releaseData.tagName
                if (CURRENT_VERSION == tagName
                    || (checkRemember && settingsDao.rememberVersion == tagName)
                ) {
                    // 当前版本是最新版本
                    Result.success(VersionDto(tagName, releaseData.htmlUrl, false))
                } else {
                    // 当前版本不是最新版本
                    Result.success(VersionDto(tagName, releaseData.htmlUrl, true))
                }
            }
            else -> Result.failure(it.exceptionOrNull()!!)
        }
    }

    fun rememberVersion(version: String?) {
        settingsDao.rememberVersion = version
    }

}
package io.github.vrcmteam.vrcm.presentation.supports

import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.storage.SettingsDao

class VersionManager(
    private val gitHubApi: GitHubApi,
    private val settingsDao: SettingsDao
) {
    companion object {
        private const val CURRENT_VERSION = "1.0.1"
    }


    /**
     * 获取最新的版本号,和版本链接
     * 如果返回不为null，则表示有新版本
     * @param checkRemember 是否检查记住版本
     * @return 版本号
     */
    suspend fun checkVersion(checkRemember: Boolean): Pair<String,String>? = gitHubApi.latestRelease().let {
        when {
            it.isSuccess -> {
                val releaseData = it.getOrNull()!!
                if (CURRENT_VERSION == releaseData.tagName
                    || (checkRemember && settingsDao.rememberVersion == releaseData.tagName)
                ) {
                    null
                } else {
                    releaseData.tagName to releaseData.htmlUrl
                }
            }

            else -> null
        }
    }

    fun rememberVersion(version: String?) {
        settingsDao.rememberVersion = version
    }

}
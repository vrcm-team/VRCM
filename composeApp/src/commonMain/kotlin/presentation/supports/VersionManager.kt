package io.github.vrcmteam.vrcm.presentation.supports

import io.github.vrcmteam.vrcm.network.api.github.GitHubApi

class VersionManager(
    private val gitHubApi: GitHubApi
) {
    companion object{
        private const val CURRENT_VERSION = "1.0.0"
    }

    /**
     * 获取最新的版本号
     * 如果返回不为null，则表示有新版本
     * @return 版本号
     */
    suspend fun checkVersion(): String? = gitHubApi.latestRelease().let {
        when {
            it.isSuccess -> {
                val releaseData = it.getOrNull()!!
                if (releaseData.tagName == CURRENT_VERSION) {
                    null
                }else{
                    releaseData.htmlUrl
                }
            }
            else -> null
        }
    }
}
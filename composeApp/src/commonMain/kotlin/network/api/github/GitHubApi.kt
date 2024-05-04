package io.github.vrcmteam.vrcm.network.api.github

import io.github.vrcmteam.vrcm.network.api.github.data.GitHubReleaseData
import io.github.vrcmteam.vrcm.network.extensions.result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class GitHubApi(
    private val client: HttpClient
) {

    companion object{
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/KAMO030/MyBatis-Flex-Kotlin/releases/latest"
    }
    suspend fun latestRelease(): Result<GitHubReleaseData> =
            client.get(LATEST_RELEASE_URL).result{ body() }

}
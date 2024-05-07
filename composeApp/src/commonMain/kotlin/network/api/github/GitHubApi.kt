package io.github.vrcmteam.vrcm.network.api.github

import io.github.vrcmteam.vrcm.network.api.github.data.ReleaseData
import io.github.vrcmteam.vrcm.network.extensions.ifOKOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class GitHubApi(
    private val client: HttpClient
) {

    companion object{
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/vrcm-team/VRCM/releases/latest"
    }
    suspend fun latestRelease(): Result<ReleaseData> =
        runCatching {
            client.get(LATEST_RELEASE_URL).ifOKOrThrow { body<ReleaseData>() }
        }


}
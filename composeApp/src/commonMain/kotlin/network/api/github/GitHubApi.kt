package io.github.vrcmteam.vrcm.network.api.github

import io.github.vrcmteam.vrcm.network.api.github.data.ReleaseData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class GitHubApi(
    private val client: HttpClient
) {

    suspend fun latestRelease(releaseUrl: String): Result<ReleaseData> =
        runCatching {
            client.get(releaseUrl) {
                if (url.host == "api.github.com") {
                    githubAuthToken()?.let { token ->
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }.checkSuccess<ReleaseData>()
        }

}

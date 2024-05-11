package io.github.vrcmteam.vrcm.network.api.github

import io.github.vrcmteam.vrcm.network.api.github.data.ReleaseData
import io.github.vrcmteam.vrcm.network.extensions.ifOKOrThrow
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class GitHubApi(
    private val client: HttpClient
) {

    suspend fun latestRelease(releaseUrl: String): Result<ReleaseData> =
        runCatching {
            client.get(releaseUrl).ifOKOrThrow { body<ReleaseData>() }
        }


}
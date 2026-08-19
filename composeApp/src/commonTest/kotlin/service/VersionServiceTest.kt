package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionServiceTest {
    @Test
    fun triesMirrorsWhenOfficialReleaseCheckFails() = runBlocking {
        val requestedHosts = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedHosts += request.url.host
                    respond("unavailable", HttpStatusCode.ServiceUnavailable)
                }
            }
        }

        val result = VersionService(GitHubApi(client), SettingsDao(MapSettings())).checkVersion(false)

        assertEquals(listOf("api.github.com", "ghfast.top", "gh-proxy.com"), requestedHosts)
        assertTrue(result.isFailure)
        client.close()
    }
}

package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.AppUpdateSource
import io.github.vrcmteam.vrcm.di.modules.createNetworkJson
import io.github.vrcmteam.vrcm.network.api.appstore.AppStoreApi
import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        val result = versionService(client).checkVersion(AppUpdateSource.GitHub, checkRemember = false)

        assertEquals(listOf("api.github.com", "ghfast.top", "gh-proxy.com"), requestedHosts)
        assertTrue(result.isFailure)
        client.close()
    }

    @Test
    fun appStoreCheckOnlyPromptsForVersionsNewerThanTheInstalledOne() = runBlocking {
        var storeVersion = "999.0.0"
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    appStoreLookupResponse("""[{"trackId":123,"version":"$storeVersion","releaseNotes":"Fixes"}]""")
                }
            }
        }
        val service = versionService(client)
        val source = AppUpdateSource.AppStore(bundleId = "io.github.vrcmteam.vrcm", region = "JP")

        val newer = service.checkVersion(source, checkRemember = false).getOrThrow()
        assertTrue(newer.hasNewVersion)
        assertEquals("itms-apps://apps.apple.com/app/id123", newer.htmlUrl)
        assertEquals("Fixes", newer.body)

        // TestFlight、开发版可能比商店版本还新，不能提示"更新"到旧版本
        storeVersion = "0.0.1"
        assertFalse(service.checkVersion(source, checkRemember = false).getOrThrow().hasNewVersion)
        client.close()
    }

    @Test
    fun appStoreCheckFallsBackToDefaultStoreAndTreatsUnlistedAppAsUpToDate() = runBlocking {
        val requestedCountries = mutableListOf<String?>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedCountries += request.url.parameters["country"]
                    appStoreLookupResponse("[]")
                }
            }
        }

        val result = versionService(client).checkVersion(
            AppUpdateSource.AppStore(bundleId = "io.github.vrcmteam.vrcm", region = "JP"),
            checkRemember = false,
        )

        assertEquals(listOf("JP", null), requestedCountries)
        assertFalse(result.getOrThrow().hasNewVersion)
        client.close()
    }

    @Test
    fun versionsCompareNumericallyPerSegment() {
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
        assertFalse(isNewerVersion("1.9.0", "1.10.0"))
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        assertTrue(isNewerVersion("1.2.1", "1.2"))
    }

    private fun versionService(client: HttpClient) =
        VersionService(GitHubApi(client), AppStoreApi(client, createNetworkJson()), SettingsDao(MapSettings()))

    // 真实接口的 Content-Type 是 text/javascript，不能依赖 ContentNegotiation 解码
    private fun MockRequestHandleScope.appStoreLookupResponse(results: String) =
        respond(
            """{"results":$results}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "text/javascript; charset=utf-8"),
        )
}

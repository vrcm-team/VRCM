package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.network.api.github.GitHubApi
import io.github.vrcmteam.vrcm.storage.SettingsDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionServiceTest {
    @Test
    fun dismissedStartupVersionStaysHiddenOnlyForCurrentSession() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                repeat(3) {
                    addHandler {
                        respond(
                            content = releaseJson,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val settingsDao = SettingsDao(MapSettings())
        val service = VersionService(GitHubApi(client), settingsDao)

        try {
            assertTrue(service.checkVersion(checkRemember = true).getOrThrow().hasNewVersion)

            service.dismissVersionForSession(RELEASE_TAG)

            assertFalse(service.checkVersion(checkRemember = true).getOrThrow().hasNewVersion)
            assertTrue(service.checkVersion(checkRemember = false).getOrThrow().hasNewVersion)
            assertNull(settingsDao.rememberVersion)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val RELEASE_TAG = "v99.0.0"
        val releaseJson = """{
            "assets": [],
            "assets_url": "https://example.com/assets",
            "author": {
                "avatar_url": "",
                "events_url": "",
                "followers_url": "",
                "following_url": "",
                "gists_url": "",
                "gravatar_id": "",
                "html_url": "",
                "id": 1,
                "login": "tester",
                "node_id": "author",
                "organizations_url": "",
                "received_events_url": "",
                "repos_url": "",
                "site_admin": false,
                "starred_url": "",
                "subscriptions_url": "",
                "type": "User",
                "url": ""
            },
            "body": "Release notes",
            "created_at": "2026-08-11T00:00:00Z",
            "draft": false,
            "html_url": "https://example.com/release",
            "id": 1,
            "name": "Release",
            "node_id": "release",
            "prerelease": false,
            "published_at": "2026-08-11T00:00:00Z",
            "tag_name": "$RELEASE_TAG",
            "tarball_url": "",
            "target_commitish": "main",
            "upload_url": "",
            "zipball_url": "",
            "url": ""
        }"""
    }
}

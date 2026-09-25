package io.github.vrcmteam.vrcm.service

import com.russhwolf.settings.MapSettings
import io.github.vrcmteam.vrcm.network.api.favorite.FavoriteApi
import io.github.vrcmteam.vrcm.network.api.favorite.data.FavoriteData
import io.github.vrcmteam.vrcm.storage.FavoriteLocalDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteServiceRemovalTest {
    @Test
    fun remoteRemovalUsesTheFavoriteRecordId() = runBlocking {
        var deletedMethod: HttpMethod? = null
        var deletedPath: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/1/auth/user/favoritelimits" -> respond(
                            content = favoriteLimitsJson(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                        else -> {
                            deletedMethod = request.method
                            deletedPath = request.url.encodedPath
                            respond("", HttpStatusCode.OK)
                        }
                    }
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val service = FavoriteService(FavoriteApi(client), FavoriteLocalDao(MapSettings()))

        try {
            service.removeFavorite(
                FavoriteData(
                    favoriteId = "wrld_target",
                    id = "fvrt_record",
                    tags = listOf("worlds1"),
                    type = "world",
                )
            )

            assertEquals(HttpMethod.Delete, deletedMethod)
            assertEquals("/api/1/favorites/fvrt_record", deletedPath)
        } finally {
            service.dispose()
            client.close()
        }
    }
}

private fun favoriteLimitsJson() = """
    {
      "maxFavoriteGroups":{"avatar":1,"friend":1,"world":1},
      "maxFavoritesPerGroup":{"avatar":100,"friend":100,"world":100},
      "defaultMaxFavoriteGroups":1,
      "defaultMaxFavoritesPerGroup":100
    }
""".trimIndent()

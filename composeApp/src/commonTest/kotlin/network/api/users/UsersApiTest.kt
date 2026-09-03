package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UsersApiTest {
    @Test
    fun deleteAllWorldPersistenceUsesCurrentUserPath() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    method = request.method
                    path = request.url.encodedPath
                    respond("", HttpStatusCode.OK)
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        }

        try {
            UsersApi(client).deleteAllWorldPersistence("usr_current")

            assertEquals(HttpMethod.Delete, method)
            assertEquals("/api/1/users/usr_current/persist", path)
        } finally {
            client.close()
        }
    }

    @Test
    fun deleteAllWorldPersistencePropagatesNonSuccessResponse() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("denied", HttpStatusCode.Forbidden)
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        }

        try {
            val error = assertFailsWith<VRCApiException> {
                UsersApi(client).deleteAllWorldPersistence("usr_current")
            }

            assertEquals(HttpStatusCode.Forbidden.value, error.code)
            assertEquals("denied", error.bodyText)
        } finally {
            client.close()
        }
    }
}

package io.github.vrcmteam.vrcm.network.api.users

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsersApiWorldPersistenceTest {
    @Test
    fun existenceCheckUsesExpectedGetPathAndMapsOkToPresent() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = persistenceClient(HttpStatusCode.OK) { requestMethod, requestPath ->
            method = requestMethod
            path = requestPath
        }

        val exists = UsersApi(client).hasWorldPersistence("usr_current", "wrld_target")

        assertTrue(exists)
        assertEquals(HttpMethod.Get, method)
        assertEquals("/api/1/users/usr_current/wrld_target/persist/exists", path)
        client.close()
    }

    @Test
    fun notFoundExistenceResponseMapsToMissing() = runBlocking {
        val client = persistenceClient(HttpStatusCode.NotFound)

        val exists = UsersApi(client).hasWorldPersistence("usr_current", "wrld_target")

        assertFalse(exists)
        client.close()
    }

    @Test
    fun deletionUsesExpectedDeletePath() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = persistenceClient(HttpStatusCode.OK) { requestMethod, requestPath ->
            method = requestMethod
            path = requestPath
        }

        UsersApi(client).deleteWorldPersistence("usr_current", "wrld_target")

        assertEquals(HttpMethod.Delete, method)
        assertEquals("/api/1/users/usr_current/wrld_target/persist", path)
        client.close()
    }

    @Test
    fun deletionTreatsNotFoundAsAlreadyMissing() = runBlocking {
        val client = persistenceClient(HttpStatusCode.NotFound)

        UsersApi(client).deleteWorldPersistence("usr_current", "wrld_target")

        client.close()
    }

    private fun persistenceClient(
        status: HttpStatusCode,
        onRequest: (HttpMethod, String) -> Unit = { _, _ -> },
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                onRequest(request.method, request.url.encodedPath)
                respond(content = "", status = status)
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
    }
}

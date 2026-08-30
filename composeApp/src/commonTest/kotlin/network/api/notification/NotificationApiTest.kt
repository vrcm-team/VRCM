package io.github.vrcmteam.vrcm.network.api.notification

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

class NotificationApiTest {
    @Test
    fun currentNotificationDeletionUsesDeleteEndpoint() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = notificationClient { method, path -> requests += method to path }

        NotificationApi(client).deleteNotificationV2("not_boop")

        assertEquals(listOf(HttpMethod.Delete to "/api/1/notifications/not_boop"), requests)
        client.close()
    }

    @Test
    fun legacyNotificationHideKeepsPutEndpoint() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = notificationClient { method, path -> requests += method to path }

        NotificationApi(client).deleteNotification("not_friend_request")

        assertEquals(
            listOf(HttpMethod.Put to "/api/1/auth/user/notifications/not_friend_request/hide"),
            requests,
        )
        client.close()
    }

    @Test
    fun markAsReadUsesSeeEndpointWithoutDeleting() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = notificationClient { method, path -> requests += method to path }

        NotificationApi(client).markNotificationAsRead("not_unread")

        assertEquals(
            listOf(HttpMethod.Put to "/api/1/auth/user/notifications/not_unread/see"),
            requests,
        )
        client.close()
    }

    private fun notificationClient(onRequest: (HttpMethod, String) -> Unit) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                onRequest(request.method, request.url.encodedPath)
                respond(
                    content = """{"success":{"message":"ok","status_code":200}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}

package io.github.vrcmteam.vrcm.network.api.users

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UsersApiBoopTest {
    @Test
    fun customBoopOnlySendsEmojiId() = runBlocking {
        var requestBody = ""
        val client = boopClient { request ->
            requestBody = request.bodyText()
            HttpStatusCode.OK
        }

        UsersApi(client).boop("usr_friend", emojiId = "default_heart")

        assertEquals("{\"emojiId\":\"default_heart\"}", requestBody)
        client.close()
    }

    @Test
    fun defaultBoopSendsEmptyObject() = runBlocking {
        var requestBody = ""
        val client = boopClient { request ->
            requestBody = request.bodyText()
            HttpStatusCode.OK
        }

        UsersApi(client).boop("usr_friend", emojiId = null)

        assertEquals("{}", requestBody)
        client.close()
    }

    @Test
    fun rateLimitIsRecognizedAsBoopCooldown() = runBlocking {
        val client = boopClient { HttpStatusCode.TooManyRequests }

        val error = assertFailsWith<VRCApiException> {
            UsersApi(client).boop("usr_friend", emojiId = null)
        }

        assertTrue(error.isBoopCooldown())
        client.close()
    }

    @Test
    fun forbiddenRecipientIsRecognizedAsBoopDisabled() = runBlocking {
        val client = boopClient { HttpStatusCode.Forbidden }

        val error = assertFailsWith<VRCApiException> {
            UsersApi(client).boop("usr_friend", emojiId = null)
        }

        assertTrue(error.isBoopDisabled())
        client.close()
    }

    private fun boopClient(status: (HttpRequestData) -> HttpStatusCode) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val responseStatus = status(request)
                respond(
                    content = if (responseStatus == HttpStatusCode.OK) {
                        """{"success":{"message":"ok","status_code":200}}"""
                    } else {
                        "cooldown"
                    },
                    status = responseStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}

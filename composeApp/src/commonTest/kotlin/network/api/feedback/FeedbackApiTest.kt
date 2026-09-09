package io.github.vrcmteam.vrcm.network.api.feedback

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeedbackApiTest {
    @Test
    fun reportUserSendsTheSupportedBehaviorReport() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = feedbackClient { request ->
            capturedRequest = request
            HttpStatusCode.OK
        }

        FeedbackApi(client).reportUser("usr_target")

        val request = requireNotNull(capturedRequest)
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/feedback/usr_target/user", request.url.encodedPath)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        assertEquals("user", body.getValue("contentType").jsonPrimitive.content)
        assertEquals("behavior-hacking", body.getValue("reason").jsonPrimitive.content)
        assertEquals("report", body.getValue("type").jsonPrimitive.content)
        client.close()
    }

    @Test
    fun reportUserPropagatesRejectedSubmission() = runBlocking {
        val client = feedbackClient { HttpStatusCode.Forbidden }

        val error = assertFailsWith<VRCApiException> {
            FeedbackApi(client).reportUser("usr_target")
        }

        assertEquals(HttpStatusCode.Forbidden.value, error.code)
        client.close()
    }

    private fun feedbackClient(status: (HttpRequestData) -> HttpStatusCode) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val responseStatus = status(request)
                respond(
                    content = if (responseStatus == HttpStatusCode.OK) "{}" else "rejected",
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

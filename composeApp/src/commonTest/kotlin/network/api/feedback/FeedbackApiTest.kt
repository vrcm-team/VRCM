package io.github.vrcmteam.vrcm.network.api.feedback

import io.github.vrcmteam.vrcm.network.api.feedback.data.ModerationReportRequest
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
import kotlin.test.assertFalse

class FeedbackApiTest {
    @Test
    fun submitReportPostsTheSelectedModerationReport() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = feedbackClient { request ->
            capturedRequest = request
            HttpStatusCode.OK to "{}"
        }

        FeedbackApi(client).submitReport(
            ModerationReportRequest(
                type = "user",
                category = "behavior",
                reason = "harassing",
                contentId = "usr_target",
            )
        )

        val request = requireNotNull(capturedRequest)
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/moderationReports", request.url.encodedPath)
        assertEquals(ContentType.Application.Json, request.body.contentType)
        assertEquals("user", body.getValue("type").jsonPrimitive.content)
        assertEquals("behavior", body.getValue("category").jsonPrimitive.content)
        assertEquals("harassing", body.getValue("reason").jsonPrimitive.content)
        assertEquals("usr_target", body.getValue("contentId").jsonPrimitive.content)
        // 没填补充说明时不发 description，避免把空串当成用户输入
        assertFalse("description" in body)
        client.close()
    }

    @Test
    fun submitReportPropagatesRejectedSubmission() = runBlocking {
        val client = feedbackClient { HttpStatusCode.Forbidden to "rejected" }

        val error = assertFailsWith<VRCApiException> {
            FeedbackApi(client).submitReport(
                ModerationReportRequest(type = "user", category = "behavior", reason = "other", contentId = "usr_target")
            )
        }

        assertEquals(HttpStatusCode.Forbidden.value, error.code)
        client.close()
    }

    @Test
    fun reportConfigReadsOnlyTheReportSectionsOfTheConfig() = runBlocking {
        val client = feedbackClient { request ->
            assertEquals("/config", request.url.encodedPath)
            HttpStatusCode.OK to """
                {
                  "clientApiKey": "ignored",
                  "reportOptions": {"group": {"group": ["sexual", "other"], "groupstore": ["billing"]}},
                  "reportCategories": {"group": {"text": "Group", "tooltip": "", "order": 18}},
                  "reportReasons": {"sexual": {"text": "Sexual Content", "tooltip": ""}}
                }
            """.trimIndent()
        }

        val config = FeedbackApi(client).reportConfig()

        assertEquals(listOf("sexual", "other"), config.reportOptions.getValue("group").getValue("group"))
        assertEquals(18, config.reportCategories.getValue("group").order)
        assertEquals("Sexual Content", config.reportReasons.getValue("sexual").text)
        client.close()
    }

    private fun feedbackClient(respondWith: (HttpRequestData) -> Pair<HttpStatusCode, String>) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val (status, content) = respondWith(request)
                respond(
                    content = content,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}

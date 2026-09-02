package io.github.vrcmteam.vrcm.network.api.invite

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InviteApiRequestInviteTest {
    @Test
    fun defaultRequestUsesSlotZero() = runBlocking {
        var method: HttpMethod? = null
        var path = ""
        var contentType: ContentType? = null
        var body = ""
        val client = requestInviteClient { request ->
            method = request.method
            path = request.url.encodedPath
            contentType = request.body.contentType
            body = request.bodyText()
            HttpStatusCode.OK
        }

        InviteApi(client).requestInvite("usr_friend")

        assertEquals(HttpMethod.Post, method)
        assertEquals("/api/1/requestInvite/usr_friend", path)
        assertEquals(ContentType.Application.Json, contentType?.withoutParameters())
        assertEquals("{\"requestSlot\":0}", body)
        client.close()
    }

    @Test
    fun serverFailureIsReturnedToCaller() = runBlocking {
        val client = requestInviteClient { HttpStatusCode.Forbidden }

        val error = assertFailsWith<VRCApiException> {
            InviteApi(client).requestInvite("usr_not_friend")
        }

        assertEquals(HttpStatusCode.Forbidden.value, error.code)
        client.close()
    }

    private fun requestInviteClient(
        status: (HttpRequestData) -> HttpStatusCode,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val responseStatus = status(request)
                respond(
                    content = if (responseStatus == HttpStatusCode.OK) "{}" else "forbidden",
                    status = responseStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
}

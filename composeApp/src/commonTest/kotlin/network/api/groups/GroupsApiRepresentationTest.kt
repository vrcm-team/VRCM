package io.github.vrcmteam.vrcm.network.api.groups

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
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

class GroupsApiRepresentationTest {
    @Test
    fun representationUpdateUsesPutWithOnlyTheRequestedState() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respond(
                        content = """{"success":{"message":"updated","status_code":200}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        GroupsApi(client).updateRepresentation(GROUP_ID, isRepresenting = true)

        val request = checkNotNull(capturedRequest)
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/groups/$GROUP_ID/representation", request.url.encodedPath)
        assertEquals("{\"isRepresenting\":true}", request.bodyText())
        client.close()
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private companion object {
        const val GROUP_ID = "grp_00000000-0000-0000-0000-000000000001"
    }
}

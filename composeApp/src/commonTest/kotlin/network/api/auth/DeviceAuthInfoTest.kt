package io.github.vrcmteam.vrcm.network.api.auth

import io.github.vrcmteam.vrcm.di.modules.createNetworkJson
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DeviceAuthInfoTest {
    @Test
    fun readsPendingLoginOriginUsingAGetQueryWithoutApprovingIt() = runBlocking {
        withResponse("""{"createdAt":1792964421294,"geolocation":{"city":"Toronto","country":"CA","region":"Ontario","regionCode":"ON"},"futureField":true}""") { api ->
            val info = api.deviceAuthInfo("F36XWSHN")
            assertEquals(1792964421294, info.createdAt)
            assertEquals("Toronto", info.geolocation?.city)
            assertEquals("CA", info.geolocation?.country)
            assertEquals("Ontario", info.geolocation?.region)
            assertEquals("ON", info.geolocation?.regionCode)
        }
        withResponse("""{"createdAt":1792964421294,"geolocation":{"country":"CA"}}""") { api ->
            val info = api.deviceAuthInfo("F36XWSHN")
            assertEquals("CA", info.geolocation?.country)
            assertNull(info.geolocation?.city)
        }
        withResponse("""{"createdAt":1792964421294,"geolocation":null}""") { api ->
            assertNull(api.deviceAuthInfo("F36XWSHN").geolocation)
        }
    }

    @Test
    fun expiredUnauthorizedAndMalformedResponsesCannotBecomeConfirmationDetails() = runBlocking {
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.Unauthorized)) {
            withResponse("""{"error":{"message":"Unavailable","status_code":${status.value}}}""", status) { api ->
                assertEquals(status.value, assertFailsWith<VRCApiException> { api.deviceAuthInfo("F36XWSHN") }.code)
            }
        }
        withResponse("""{"geolocation":{"country":"CA"}}""") { api ->
            assertFailsWith<JsonConvertException> { api.deviceAuthInfo("F36XWSHN") }
        }
    }

    private suspend fun withResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK,
        block: suspend (AuthApi) -> Unit) {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/auth/device/info", request.url.encodedPath)
                    assertEquals(listOf("F36XWSHN"), request.url.parameters.getAll("userCode"))
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            install(ContentNegotiation) { json(createNetworkJson()) }
        }
        try { block(AuthApi(client)) } finally { client.close() }
    }
}

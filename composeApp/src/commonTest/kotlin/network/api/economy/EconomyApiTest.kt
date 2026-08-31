package io.github.vrcmteam.vrcm.network.api.economy

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
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
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class EconomyApiTest {
    @Test
    fun getCreditsBalanceUsesEconomyBalanceEndpointAndDecodesRequiredBalance() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            """{"balance":42,"noTransactions":false,"tiliaResponse":true}"""
        }

        try {
            val result = EconomyApi(client).getCreditsBalance("usr_123")

            assertEquals(HttpMethod.Get, method)
            assertEquals("/api/1/user/usr_123/economy/balance", path)
            assertEquals(42L, result.balance)
        } finally {
            client.close()
        }
    }

    @Test
    fun getCreditsBalanceRejectsResponseWithoutBalanceInsteadOfShowingZero() = runBlocking {
        val client = testClient { """{"noTransactions":true}""" }

        try {
            assertFails {
                EconomyApi(client).getCreditsBalance("usr_123")
            }
        } finally {
            client.close()
        }
        Unit
    }

    @Test
    fun getCreditsBalanceRejectsInvalidUserIdBeforeSendingRequest() = runBlocking {
        var requestCount = 0
        val client = testClient {
            requestCount++
            """{"balance":42}"""
        }

        try {
            assertFailsWith<IllegalArgumentException> {
                EconomyApi(client).getCreditsBalance("usr_123/balance")
            }
            assertEquals(0, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun getCreditsBalancePreservesUnauthorizedStatusForSessionRecovery() = runBlocking {
        val client = testClient(status = HttpStatusCode.Unauthorized) {
            """{"error":{"message":"Unauthorized","status_code":401}}"""
        }

        try {
            val error = assertFailsWith<VRCApiException> {
                EconomyApi(client).getCreditsBalance("usr_123")
            }

            assertEquals(401, error.code)
        } finally {
            client.close()
        }
    }

    private fun testClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: (io.ktor.client.request.HttpRequestData) -> String,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                respond(
                    content = responseBody(request),
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
}

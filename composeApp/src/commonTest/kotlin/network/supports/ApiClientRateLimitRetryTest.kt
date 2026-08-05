package io.github.vrcmteam.vrcm.network.supports

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiClientRateLimitRetryTest {
    @Test
    fun getAndHeadRetryWithIncreasingDelaysAndDoNotNotifyAfterRecovery() = runTest {
        listOf(HttpMethod.Get, HttpMethod.Head).forEach { method ->
            var requestCount = 0
            val delays = mutableListOf<Long>()
            val notices = ApiNoticeCenter()
            val client = rateLimitClient(notices, delays) {
                requestCount++
                respond(
                    content = "",
                    status = if (requestCount <= 2) {
                        HttpStatusCode.TooManyRequests
                    } else {
                        HttpStatusCode.OK
                    },
                )
            }

            val response = client.request("/avatars/avtr_1") { this.method = method }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(3, requestCount)
            assertEquals(listOf(1_000L, 2_000L), delays)
            assertNull(notices.activeNotice.value)
            client.close()
        }
    }

    @Test
    fun retryAfterOverridesTheFallbackDelay() = runTest {
        var requestCount = 0
        val delays = mutableListOf<Long>()
        val notices = ApiNoticeCenter()
        val client = rateLimitClient(notices, delays) {
            requestCount++
            if (requestCount == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.RetryAfter, "7"),
                )
            } else {
                respond("", HttpStatusCode.OK)
            }
        }

        client.request("/avatars/avtr_1") { method = HttpMethod.Get }

        assertEquals(listOf(7_000L), delays)
        assertNull(notices.activeNotice.value)
        client.close()
    }

    @Test
    fun terminalReadRateLimitPublishesAfterFiveRetries() = runTest {
        var requestCount = 0
        val delays = mutableListOf<Long>()
        val notices = ApiNoticeCenter()
        val client = rateLimitClient(notices, delays) {
            requestCount++
            respond("", HttpStatusCode.TooManyRequests)
        }

        client.request("/avatars/avtr_1") { method = HttpMethod.Get }

        assertEquals(6, requestCount)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), delays)
        assertEquals(ApiNotice.RateLimited, notices.activeNotice.value)
        client.close()
    }

    @Test
    fun mutationDoesNotRetryAndPublishesImmediately() = runTest {
        var requestCount = 0
        val delays = mutableListOf<Long>()
        val notices = ApiNoticeCenter()
        val client = rateLimitClient(notices, delays) {
            requestCount++
            respond("", HttpStatusCode.TooManyRequests)
        }

        client.request("/favorites") { method = HttpMethod.Post }

        assertEquals(1, requestCount)
        assertEquals(emptyList(), delays)
        assertEquals(ApiNotice.RateLimited, notices.activeNotice.value)
        client.close()
    }

    private fun rateLimitClient(
        notices: ApiNoticeCenter,
        delays: MutableList<Long>,
        handler: MockRequestHandler,
    ) = HttpClient(MockEngine) {
        engine { addHandler(handler) }
        configureApiClient(notices) { delays += it }
    }
}

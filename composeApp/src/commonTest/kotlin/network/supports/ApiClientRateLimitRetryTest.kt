package io.github.vrcmteam.vrcm.network.supports

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiClientRateLimitRetryTest {
    @Test
    fun getRetriesAfterTooManyRequests() = runTest {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requestCount++
                    respond(
                        content = "",
                        status = if (requestCount == 1) {
                            HttpStatusCode.TooManyRequests
                        } else {
                            HttpStatusCode.OK
                        },
                    )
                }
            }
            ApiClientDefaultBuilder()
        }

        val response = client.get("/auth/user/friends")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, requestCount)
        client.close()
    }
}

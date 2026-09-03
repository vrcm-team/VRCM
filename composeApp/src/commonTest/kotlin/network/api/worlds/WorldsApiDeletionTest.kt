package io.github.vrcmteam.vrcm.network.api.worlds

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorldsApiDeletionTest {
    @Test
    fun deletionUsesTheWorldResourceAndDeleteMethod() = runBlocking {
        var recordedMethod: HttpMethod? = null
        var recordedPath: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    recordedMethod = request.method
                    recordedPath = request.url.encodedPath
                    respond("", HttpStatusCode.OK)
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        }

        WorldsApi(client).deleteWorld("wrld_owned")

        assertEquals(HttpMethod.Delete, recordedMethod)
        assertEquals("/api/1/worlds/wrld_owned", recordedPath)
        client.close()
    }

    @Test
    fun deletionRejectsNonSuccessResponses() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("not allowed", HttpStatusCode.Forbidden)
                }
            }
            defaultRequest { url("https://api.vrchat.cloud/api/1/") }
        }

        val error = assertFailsWith<VRCApiException> {
            WorldsApi(client).deleteWorld("wrld_other")
        }

        assertEquals(HttpStatusCode.Forbidden.value, error.code)
        client.close()
    }
}

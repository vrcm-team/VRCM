package io.github.vrcmteam.vrcm.network.api.instances

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InstancesApiTest {
    @Test
    fun closeInstanceUsesOnlyTheCanonicalDeletePath() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = closeInstanceClient { request ->
            capturedRequest = request
            HttpStatusCode.OK
        }

        val closed = InstancesApi(client).closeInstance(WORLD_ID, INSTANCE_ID)

        val request = checkNotNull(capturedRequest)
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/instances/$LOCATION", request.url.encodedPath)
        assertTrue(request.url.encodedQuery.isEmpty())
        assertEquals(LOCATION, closed.location)
        assertEquals("2026-08-31T08:00:00.000Z", closed.closedAt)
        client.close()
    }

    @Test
    fun closeInstancePreservesPermissionFailure() = runBlocking {
        val client = closeInstanceClient { HttpStatusCode.Forbidden }

        val error = assertFailsWith<VRCApiException> {
            InstancesApi(client).closeInstance(WORLD_ID, INSTANCE_ID)
        }

        assertEquals(403, error.code)
        client.close()
    }

    private fun closeInstanceClient(
        status: (HttpRequestData) -> HttpStatusCode,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val responseStatus = status(request)
                respond(
                    content = if (responseStatus == HttpStatusCode.OK) {
                        closedInstanceJson()
                    } else {
                        "permission denied"
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

    private fun closedInstanceJson(): String =
        """
        {
          "active":false,
          "canRequestInvite":false,
          "capacity":16,
          "clientNumber":"1",
          "closedAt":"2026-08-31T08:00:00.000Z",
          "full":false,
          "hidden":null,
          "id":"$LOCATION",
          "instanceId":"$INSTANCE_ID",
          "location":"$LOCATION",
          "n_users":0,
          "name":"12345",
          "ownerId":"$USER_ID",
          "permanent":false,
          "photonRegion":"us",
          "platforms":{"android":0,"ios":0,"standalonewindows":0},
          "queueEnabled":false,
          "queueSize":0,
          "recommendedCapacity":16,
          "region":"us",
          "secureName":"secure",
          "strict":false,
          "tags":[],
          "type":"private",
          "userCount":0,
          "world":{
            "authorId":"$USER_ID",
            "authorName":"Author",
            "capacity":16,
            "created_at":null,
            "description":null,
            "favorites":0,
            "featured":false,
            "heat":0,
            "id":"$WORLD_ID",
            "imageUrl":"",
            "labsPublicationDate":"",
            "name":"World",
            "namespace":null,
            "organization":"",
            "popularity":0,
            "publicationDate":"",
            "recommendedCapacity":16,
            "releaseStatus":"public",
            "tags":[],
            "thumbnailImageUrl":null,
            "udonProducts":[],
            "unityPackages":[],
            "updated_at":null,
            "version":1,
            "visits":0
          },
          "worldId":"$WORLD_ID"
        }
        """.trimIndent()

    private companion object {
        const val WORLD_ID = "wrld_00000000-0000-0000-0000-000000000001"
        const val USER_ID = "usr_00000000-0000-0000-0000-000000000002"
        const val INSTANCE_ID = "12345~region(us)"
        const val LOCATION = "$WORLD_ID:$INSTANCE_ID"
    }
}

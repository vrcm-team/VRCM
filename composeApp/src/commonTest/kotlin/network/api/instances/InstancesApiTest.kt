package io.github.vrcmteam.vrcm.network.api.instances

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCreationOptions
import io.github.vrcmteam.vrcm.network.api.instances.data.MinimumAvatarPerformance
import io.github.vrcmteam.vrcm.network.supports.VRCApiException
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstancesApiTest {
    @Test
    fun groupCreationSendsEverySupportedAdvancedFieldAndUsesServerResponse() = runBlocking {
        var method: HttpMethod? = null
        var path = ""
        var body = JsonObject(emptyMap())
        val client = instanceClient { request ->
            method = request.method
            path = request.url.encodedPath
            body = NETWORK_JSON.parseToJsonElement(request.bodyText()) as JsonObject
        }

        try {
            val created = InstancesApi(client).createInstance(
                InstanceCreationOptions(
                    worldId = " wrld_test ",
                    accessType = AccessType.GroupMembers,
                    region = RegionType.Eu,
                    userId = "usr_current",
                    queueEnabled = true,
                    groupId = " grp_test ",
                    roleIds = listOf("grol_one", " grol_two ", "grol_one"),
                    ageGate = true,
                    displayName = "  Community Night  ",
                    minimumAvatarPerformance = MinimumAvatarPerformance.Medium,
                )
            )

            assertEquals(HttpMethod.Post, method)
            assertEquals("/instances", path)
            assertEquals("wrld_test", body.string("worldId"))
            assertEquals("group", body.string("type"))
            assertEquals("eu", body.string("region"))
            assertEquals("grp_test", body.string("ownerId"))
            assertEquals("members", body.string("groupAccessType"))
            assertEquals(
                JsonArray(listOf(JsonPrimitive("grol_one"), JsonPrimitive("grol_two"))),
                body["roleIds"],
            )
            assertEquals(true, body.getValue("queueEnabled").jsonPrimitive.boolean)
            assertEquals(true, body.getValue("ageGate").jsonPrimitive.boolean)
            assertEquals("Community Night", body.string("displayName"))
            assertEquals("Medium", body.string("minimumAvatarPerformance"))
            assertEquals("Server Name", created.displayName)
            assertEquals("instance_server", created.id)
        } finally {
            client.close()
        }
    }

    @Test
    fun standardCreationOmitsGroupOnlyFields() = runBlocking {
        var body = JsonObject(emptyMap())
        val client = instanceClient { request ->
            body = NETWORK_JSON.parseToJsonElement(request.bodyText()) as JsonObject
        }

        try {
            InstancesApi(client).createInstance(
                InstanceCreationOptions(
                    worldId = "wrld_test",
                    accessType = AccessType.InvitePlus,
                    region = RegionType.Jp,
                    userId = "usr_current",
                    displayName = "Private meetup",
                )
            )

            assertEquals("private", body.string("type"))
            assertEquals("usr_current", body.string("ownerId"))
            assertEquals(true, body.getValue("canRequestInvite").jsonPrimitive.boolean)
            assertFalse("groupAccessType" in body)
            assertFalse("roleIds" in body)
            assertFalse("queueEnabled" in body)
            assertFalse("ageGate" in body)
            assertFalse("minimumAvatarPerformance" in body)
        } finally {
            client.close()
        }
    }

    @Test
    fun invalidCrossFieldCombinationsAreRejectedBeforeNetworkWrite() = runBlocking {
        var requestCount = 0
        val client = instanceClient { requestCount++ }
        val api = InstancesApi(client)

        try {
            assertFailsWith<IllegalArgumentException> {
                api.createInstance(
                    InstanceCreationOptions(
                        worldId = "wrld_test",
                        accessType = AccessType.GroupPlus,
                        region = RegionType.Us,
                        groupId = "grp_test",
                        roleIds = listOf("grol_restricted"),
                    )
                )
            }
            assertFailsWith<IllegalArgumentException> {
                api.createInstance(
                    InstanceCreationOptions(
                        worldId = "wrld_test",
                        accessType = AccessType.Public,
                        region = RegionType.Us,
                        ageGate = true,
                    )
                )
            }
            assertEquals(0, requestCount)
        } finally {
            client.close()
        }
    }

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

    @Test
    fun closeInstanceAcceptsResponseWithoutOptionalInstanceFields() = runBlocking {
        val client = closeInstanceClient(
            status = { HttpStatusCode.OK },
            successContent = closedInstanceJson(includeOptionalFields = false),
        )

        val closed = InstancesApi(client).closeInstance(WORLD_ID, INSTANCE_ID)

        assertEquals(LOCATION, closed.location)
        assertNull(closed.active)
        assertNull(closed.ownerId)
        client.close()
    }

    private fun instanceClient(onRequest: (HttpRequestData) -> Unit) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                onRequest(request)
                respond(
                    content = INSTANCE_RESPONSE,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        install(ContentNegotiation) { json(NETWORK_JSON) }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private fun JsonObject.string(name: String): String =
        getValue(name).jsonPrimitive.content

    private fun closeInstanceClient(
        successContent: String = closedInstanceJson(),
        status: (HttpRequestData) -> HttpStatusCode,
    ) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val responseStatus = status(request)
                respond(
                    content = if (responseStatus == HttpStatusCode.OK) {
                        successContent
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

    private fun closedInstanceJson(includeOptionalFields: Boolean = true): String {
        val optionalFields = if (includeOptionalFields) {
            """
              "active":false,
              "canRequestInvite":false,
              "capacity":16,
              "hidden":null,
              "ownerId":"$USER_ID",
            """.trimIndent()
        } else {
            ""
        }
        return """
        {
          $optionalFields
          "clientNumber":"1",
          "closedAt":"2026-08-31T08:00:00.000Z",
          "full":false,
          "id":"$LOCATION",
          "instanceId":"$INSTANCE_ID",
          "location":"$LOCATION",
          "n_users":0,
          "name":"12345",
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
    }

    private companion object {
        val NETWORK_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        val INSTANCE_RESPONSE = """
            {
              "active": true,
              "canRequestInvite": false,
              "capacity": 40,
              "clientNumber": "1",
              "displayName": "Server Name",
              "full": false,
              "hidden": null,
              "id": "instance_server",
              "instanceId": "instance_server",
              "location": "wrld_test:instance_server",
              "n_users": 0,
              "name": "instance_server",
              "ownerId": "grp_test",
              "permanent": false,
              "photonRegion": "eu",
              "platforms": {},
              "queueEnabled": true,
              "queueSize": 0,
              "recommendedCapacity": 20,
              "region": "eu",
              "secureName": "secure",
              "strict": false,
              "tags": [],
              "type": "group",
              "userCount": 0,
              "worldId": "wrld_test",
              "world": {
                "authorId": "usr_author",
                "authorName": "Author",
                "capacity": 40,
                "created_at": null,
                "description": null,
                "favorites": null,
                "featured": null,
                "heat": 0,
                "id": "wrld_test",
                "imageUrl": "",
                "labsPublicationDate": "",
                "name": "World",
                "namespace": null,
                "organization": "",
                "popularity": 0,
                "publicationDate": "",
                "recommendedCapacity": 20,
                "releaseStatus": "public",
                "tags": [],
                "thumbnailImageUrl": null,
                "udonProducts": [],
                "unityPackages": [],
                "updated_at": null,
                "version": null,
                "visits": null
              }
            }
        """.trimIndent()

        const val WORLD_ID = "wrld_00000000-0000-0000-0000-000000000001"
        const val USER_ID = "usr_00000000-0000-0000-0000-000000000002"
        const val INSTANCE_ID = "12345~region(us)"
        const val LOCATION = "$WORLD_ID:$INSTANCE_ID"
    }
}

package io.github.vrcmteam.vrcm.network.api.instances

import io.github.vrcmteam.vrcm.network.api.attributes.AccessType
import io.github.vrcmteam.vrcm.network.api.attributes.RegionType
import io.github.vrcmteam.vrcm.network.api.instances.data.InstanceCreationOptions
import io.github.vrcmteam.vrcm.network.api.instances.data.MinimumAvatarPerformance
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
            assertEquals(JsonArray(listOf(JsonPrimitive("grol_one"), JsonPrimitive("grol_two"))), body["roleIds"])
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
    }
}

package io.github.vrcmteam.vrcm.network.api.groups

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
import kotlin.test.assertNull

class GroupsApiUserGroupInstancesTest {
    /**
     * 主页「群组房间」整页都靠这一个请求：路径写错只会静默 404，
     * 而实例里 active / capacity / hidden 这些字段 VRChat 并不总是带上，
     * 少一个就把整页房间变成解析失败。
     */
    @Test
    fun userGroupInstancesReadsOwnEndpointAndToleratesOmittedInstanceFields() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respond(
                        content = USER_GROUP_INSTANCES_RESPONSE,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val instances = GroupsApi(client).getUserGroupInstances(USER_ID).instances

        val request = checkNotNull(capturedRequest)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/users/$USER_ID/instances/groups", request.url.encodedPath)

        val instance = instances.single()
        assertEquals(LOCATION, instance.location)
        // 房间归属的群组来自 ownerId，标题分段和房主信息都要用它
        assertEquals(GROUP_ID, instance.ownerId)
        assertEquals(WORLD_ID, instance.world.id)
        assertEquals(12, instance.nUsers)
        assertNull(instance.hidden)
        client.close()
    }

    private companion object {
        const val USER_ID = "usr_00000000-0000-0000-0000-000000000001"
        const val GROUP_ID = "grp_00000000-0000-0000-0000-000000000002"
        const val WORLD_ID = "wrld_00000000-0000-0000-0000-000000000003"
        const val INSTANCE_ID = "12345~group($GROUP_ID)~groupAccessType(public)~region(jp)"
        const val LOCATION = "$WORLD_ID:$INSTANCE_ID"

        // 没有 active / canRequestInvite / capacity / hidden：群组房间列表的真实响应会省略它们
        val USER_GROUP_INSTANCES_RESPONSE = """
            {
              "fetchedAt": "2026-08-31T08:00:00.000Z",
              "instances": [
                {
                  "clientNumber": "unknown",
                  "full": false,
                  "id": "$LOCATION",
                  "instanceId": "$INSTANCE_ID",
                  "location": "$LOCATION",
                  "n_users": 12,
                  "name": "12345",
                  "ownerId": "$GROUP_ID",
                  "permanent": false,
                  "photonRegion": "jp",
                  "platforms": {"android": 2, "standalonewindows": 10},
                  "queueEnabled": false,
                  "queueSize": 0,
                  "recommendedCapacity": 20,
                  "region": "jp",
                  "secureName": "secure",
                  "strict": false,
                  "tags": [],
                  "type": "group",
                  "userCount": 12,
                  "worldId": "$WORLD_ID",
                  "world": {
                    "authorId": "usr_author",
                    "authorName": "Author",
                    "capacity": 40,
                    "created_at": null,
                    "description": null,
                    "favorites": null,
                    "featured": null,
                    "heat": 0,
                    "id": "$WORLD_ID",
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
              ]
            }
        """.trimIndent()
    }
}

package io.github.vrcmteam.vrcm.network.api.inventory

import io.github.vrcmteam.vrcm.network.supports.VRCApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryApiTest {
    @Test
    fun redeemRewardUsesJsonEndpointAndDecodesCurrentRewardShapes() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        var contentType: ContentType? = null
        var body = ""
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            val outgoing = request.body as OutgoingContent.ByteArrayContent
            contentType = outgoing.contentType
            body = outgoing.bytes().decodeToString()
            """[
                {
                    "redeemedRewards":[
                        {
                            "data":{"badge":{
                                "createdAt":"2026-08-01T00:00:00Z",
                                "createdBy":"usr_creator",
                                "description":"Launch reward",
                                "fileName":"launch.png",
                                "hidden":false,
                                "id":"bdg_launch",
                                "imageUrl":"https://example.com/badge.png",
                                "isLocalizationEnabled":true,
                                "machineName":"launch_reward",
                                "name":"Launch Badge",
                                "type":"badge",
                                "updatedAt":"2026-08-02T00:00:00Z"
                            }},
                            "type":"badge"
                        },
                        {
                            "data":{"item":{
                                "attribution":{},
                                "authorId":"usr_creator",
                                "collections":["launch"],
                                "created_at":"2026-08-01T00:00:00Z",
                                "defaultAttributes":{},
                                "description":"A portable light",
                                "dropStatus":"active",
                                "equipSlots":["hand"],
                                "flags":["unique"],
                                "id":"invt_lantern",
                                "imageUrl":"https://example.com/item.png",
                                "itemType":"prop",
                                "itemTypeLabel":"Prop",
                                "metadata":{"futureField":true},
                                "name":"Lantern",
                                "notificationDetails":{},
                                "status":"live",
                                "tags":["featured"],
                                "updated_at":"2026-08-02T00:00:00Z",
                                "validateUserAttributes":true
                            }},
                            "type":"item"
                        },
                        {
                            "data":{"futureReward":{"name":"Future reward"}},
                            "type":"future-reward"
                        }
                    ],
                    "redemptionCode":"reward-code-123",
                    "futureResponseField":true
                }
            ]"""
        }

        try {
            val result = InventoryApi(client).redeemReward("reward-code-123")

            assertEquals(HttpMethod.Post, method)
            assertEquals("/api/1/reward/redeem", path)
            assertTrue(requireNotNull(contentType).match(ContentType.Application.Json))
            assertEquals("{\"code\":\"reward-code-123\"}", body)
            assertEquals(1, result.size)
            assertEquals("reward-code-123", result.single().redemptionCode)
            val rewards = result.single().redeemedRewards
            assertEquals(listOf("badge", "item", "future-reward"), rewards.map { it.type })
            assertEquals("Launch Badge", rewards[0].data.badge?.name)
            assertEquals("https://example.com/badge.png", rewards[0].data.badge?.imageUrl)
            assertEquals("Lantern", rewards[1].data.item?.name)
            assertEquals("prop", rewards[1].data.item?.itemType)
            assertEquals(listOf("hand"), rewards[1].data.item?.equipSlots)
            assertNull(rewards[2].data.badge)
            assertNull(rewards[2].data.item)
        } finally {
            client.close()
        }
    }

    @Test
    fun getTemplateUsesInventoryEndpointAndDecodesMetadata() = runBlocking {
        var method: HttpMethod? = null
        var path: String? = null
        val client = testClient { request ->
            method = request.method
            path = request.url.encodedPath
            """{
                "id":"inv_123",
                "metadata":{
                    "assets":[
                        {"type":"image","url":"https://example.com/frame.png"}
                    ],
                    "gradientStart":"#112233",
                    "gradientEnd":"#445566"
                }
            }"""
        }

        try {
            val result = InventoryApi(client).getTemplate("inv_123")

            assertEquals(HttpMethod.Get, method)
            assertEquals("/api/1/inventory/template/inv_123", path)
            assertEquals("inv_123", result.id)
            assertEquals(1, result.metadata.assets.size)
            assertEquals("image", result.metadata.assets.single().type)
            assertEquals("https://example.com/frame.png", result.metadata.assets.single().url)
            assertEquals("#112233", result.metadata.gradientStart)
            assertEquals("#445566", result.metadata.gradientEnd)
        } finally {
            client.close()
        }
    }

    @Test
    fun getTemplateUsesDefaultsWhenMetadataFieldsAreMissing() = runBlocking {
        val client = testClient { """{"id":"inv_123","metadata":{}}""" }

        try {
            val result = InventoryApi(client).getTemplate("inv_123")

            assertEquals(emptyList(), result.metadata.assets)
            assertNull(result.metadata.gradientStart)
            assertNull(result.metadata.gradientEnd)
        } finally {
            client.close()
        }
    }

    @Test
    fun getTemplateRejectsInvalidTemplateIdsBeforeSendingRequest() = runBlocking {
        var requestCount = 0
        val client = testClient {
            requestCount++
            """{"id":"inv_123"}"""
        }

        try {
            listOf("", "   ", "inv_123/asset").forEach { templateId ->
                assertFailsWith<IllegalArgumentException> {
                    InventoryApi(client).getTemplate(templateId)
                }
            }
            assertEquals(0, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun getTemplateRejectsUnauthorizedResponseInsteadOfDecodingDtoDefaults() = runBlocking {
        val client = testClient(status = HttpStatusCode.Unauthorized) {
            """{"error":{"message":"Unauthorized","status_code":401}}"""
        }

        try {
            val error = assertFailsWith<VRCApiException> {
                InventoryApi(client).getTemplate("inv_123")
            }

            assertEquals(401, error.code)
        } finally {
            client.close()
        }
    }

    private fun testClient(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: (io.ktor.client.request.HttpRequestData) -> String,
    ) =
        HttpClient(MockEngine) {
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

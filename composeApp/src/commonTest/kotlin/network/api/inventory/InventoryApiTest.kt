package io.github.vrcmteam.vrcm.network.api.inventory

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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InventoryApiTest {
    @Test
    fun getInventorySendsPagingFiltersAndSortThenDecodesCurrentResponse() = runBlocking {
        var request: io.ktor.client.request.HttpRequestData? = null
        val client = testClient { captured ->
            request = captured
            """{
                "data":[{
                    "acquisition":"unknown",
                    "attribution":null,
                    "collections":[],
                    "created_at":"2025-12-09T22:21:49.336Z",
                    "description":"Get toasty.",
                    "expiryDate":null,
                    "holderId":"usr_holder",
                    "id":"inv_campfire",
                    "imageUrl":"https://example.com/campfire.png",
                    "isArchived":true,
                    "itemType":"prop",
                    "itemTypeLabel":"Item",
                    "last_equipped":{},
                    "metadata":{
                        "animated":false,
                        "imageUrl":"https://example.com/campfire-source.png",
                        "propKind":0
                    },
                    "name":"Campfire",
                    "quantifiable":false,
                    "updated_at":"2025-12-10T01:00:00.000Z"
                }],
                "totalCount":73
            }"""
        }

        try {
            val result = InventoryApi(client).getInventory(
                n = 25,
                offset = 50,
                type = InventoryItemType.Prop,
                archived = true,
                order = InventorySortOrder.OldestCreated,
            )

            val captured = requireNotNull(request)
            assertEquals(HttpMethod.Get, captured.method)
            assertEquals("/api/1/inventory", captured.url.encodedPath)
            assertEquals("25", captured.url.parameters["n"])
            assertEquals("50", captured.url.parameters["offset"])
            assertEquals("prop", captured.url.parameters["types"])
            assertEquals("true", captured.url.parameters["archived"])
            assertEquals("oldest_created", captured.url.parameters["order"])
            assertNull(captured.url.parameters["holderId"])
            assertEquals(73, result.totalCount)
            assertEquals("inv_campfire", result.data.single().id)
            assertEquals("Campfire", result.data.single().name)
            assertEquals("prop", result.data.single().itemType)
            assertEquals(true, result.data.single().isArchived)
            assertNull(result.data.single().expiryDate)
            assertEquals("https://example.com/campfire.png", result.data.single().displayImageUrl)
        } finally {
            client.close()
        }
    }

    @Test
    fun getInventoryOmitsOptionalFiltersAndToleratesMissingItemFields() = runBlocking {
        var request: io.ktor.client.request.HttpRequestData? = null
        val client = testClient { captured ->
            request = captured
            """{"data":[{"id":"inv_minimal"}]}"""
        }

        try {
            val result = InventoryApi(client).getInventory()

            val captured = requireNotNull(request)
            assertNull(captured.url.parameters["types"])
            assertNull(captured.url.parameters["archived"])
            assertEquals("newest", captured.url.parameters["order"])
            assertEquals("inv_minimal", result.data.single().id)
            assertNull(result.data.single().name)
            assertNull(result.data.single().metadata)
            assertNull(result.totalCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun getInventoryRejectsInvalidPagingBeforeSendingRequest() = runBlocking {
        var requestCount = 0
        val client = testClient {
            requestCount++
            """{"data":[],"totalCount":0}"""
        }

        try {
            listOf(0, 101).forEach { pageSize ->
                assertFailsWith<IllegalArgumentException> {
                    InventoryApi(client).getInventory(n = pageSize)
                }
            }
            assertFailsWith<IllegalArgumentException> {
                InventoryApi(client).getInventory(offset = -1)
            }
            assertEquals(0, requestCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun getInventoryRejectsUnauthorizedResponse() = runBlocking {
        val client = testClient(status = HttpStatusCode.Unauthorized) {
            """{"error":{"message":"Unauthorized","status_code":401}}"""
        }

        try {
            val error = assertFailsWith<VRCApiException> {
                InventoryApi(client).getInventory()
            }

            assertEquals(401, error.code)
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

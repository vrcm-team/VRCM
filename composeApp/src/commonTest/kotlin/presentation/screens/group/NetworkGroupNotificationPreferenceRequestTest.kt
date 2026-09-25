package io.github.vrcmteam.vrcm.presentation.screens.group

import io.github.vrcmteam.vrcm.core.shared.SharedFlowCentre
import io.github.vrcmteam.vrcm.network.api.groups.GroupsApi
import io.github.vrcmteam.vrcm.service.data.AccountDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NetworkGroupNotificationPreferenceRequestTest {
    @Test
    fun disablingExplicitlySendsTheFalsePreference() = runBlocking {
        var capturedRequest: HttpRequestData? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respondJson(notificationMemberJson(enabled = false))
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        try {
            val member = GroupsApi(client).updateGroupNotifications(
                groupId = REPRESENTATION_GROUP_ID,
                userId = REPRESENTATION_USER_ID,
                enabled = false,
            )

            val request = checkNotNull(capturedRequest)
            assertEquals(HttpMethod.Put, request.method)
            assertEquals(
                "/groups/$REPRESENTATION_GROUP_ID/members/$REPRESENTATION_USER_ID",
                request.url.encodedPath,
            )
            assertEquals("{\"isSubscribedToAnnouncements\":false}", request.bodyTextOrNull())
            assertEquals(false, member.isSubscribedToAnnouncements)
        } finally {
            client.close()
        }
    }

    @Test
    fun updateUsesTheMemberEndpointAndPublishesAnAuthoritativeRefresh() = runBlocking {
        val account = AccountDto(userId = REPRESENTATION_USER_ID, username = "notification-test")
        SharedFlowCentre.emitAuthenticated(account)
        val sessionToken = checkNotNull(SharedFlowCentre.currentSession.value).token
        val observedRequests = mutableListOf<ObservedRequest>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    observedRequests += ObservedRequest(
                        method = request.method,
                        path = request.url.encodedPath,
                        includeRoles = request.url.parameters["includeRoles"],
                        body = request.bodyTextOrNull(),
                    )
                    when (request.method) {
                        HttpMethod.Put -> respondJson(notificationMemberJson(enabled = true))
                        HttpMethod.Get -> respondJson(
                            representationGroupJson(
                                isRepresenting = false,
                                isSubscribedToAnnouncements = true,
                            )
                        )

                        else -> error("Unexpected request: ${request.method} ${request.url}")
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        try {
            val request = NetworkGroupNotificationPreferenceRequest(
                groupsApi = GroupsApi(client),
                authService = createRepresentationAuthService(client, account),
            )

            val response = checkNotNull(
                request.update(
                    sessionToken = sessionToken,
                    groupId = REPRESENTATION_GROUP_ID,
                    userId = REPRESENTATION_USER_ID,
                    enabled = true,
                )
            )

            assertEquals(
                listOf(
                    ObservedRequest(
                        method = HttpMethod.Put,
                        path = "/groups/$REPRESENTATION_GROUP_ID/members/$REPRESENTATION_USER_ID",
                        includeRoles = null,
                        body = "{\"isSubscribedToAnnouncements\":true}",
                    ),
                    ObservedRequest(
                        method = HttpMethod.Get,
                        path = "/groups/$REPRESENTATION_GROUP_ID",
                        includeRoles = "true",
                        body = null,
                    ),
                ),
                observedRequests,
            )
            assertEquals(sessionToken, response.sessionToken)
            assertTrue(response.result.getOrThrow().myMember!!.isSubscribedToAnnouncements)
        } finally {
            client.close()
            SharedFlowCentre.emitLogout()
        }
    }

    private fun HttpRequestData.bodyTextOrNull(): String? =
        (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()

    private data class ObservedRequest(
        val method: HttpMethod,
        val path: String,
        val includeRoles: String?,
        val body: String?,
    )
}

internal fun notificationMemberJson(enabled: Boolean) = """
    {
      "id":"gmem_1",
      "userId":"$REPRESENTATION_USER_ID",
      "groupId":"$REPRESENTATION_GROUP_ID",
      "membershipStatus":"member",
      "isRepresenting":false,
      "isSubscribedToAnnouncements":$enabled,
      "roleIds":[],
      "joinedAt":"2026-01-01T00:00:00.000Z"
    }
""".trimIndent()

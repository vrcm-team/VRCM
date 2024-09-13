package io.github.vrcmteam.vrcm.network.api.notification

import io.github.vrcmteam.vrcm.network.api.attributes.*
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
import io.github.vrcmteam.vrcm.network.api.notification.data.ResponseData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*

class NotificationApi(
    private val client: HttpClient,
) {

    suspend fun fetchNotifications(limit: Int = 100): Result<List<NotificationData>> = runCatching {
        client.get(NOTIFICATIONS_API_PREFIX) {
            parameter("limit", limit)
        }.ifOK<List<NotificationData>> { body() }.getOrThrow()
    }

    suspend fun responseNotification(id: String, response: NotificationItemData.ActionData): Result<String> = runCatching {
        return client.post("$NOTIFICATIONS_API_PREFIX/$id/respond") {
            setBody(
                TextContent(
                    """{"responseData":"${response.data}","responseType":"${response.type}"}""",
                    ContentType.Application.Json
                )
            )
        }.ifOK { bodyAsText() }
    }

    suspend fun fetchNotificationsV2(
        type: String = NotificationType.All.value,
        // the only type you can see hidden content on is friendRequest
        hidden: Boolean = false,
        n: Int = 100,
        offset: Int = 0,
    ): Result<List<NotificationDataV2>> = runCatching {
        client.get {
            url { path(AUTH_API_PREFIX, USER_API_PREFIX, NOTIFICATIONS_API_PREFIX) }
            parameter("type", type)
            parameter("hidden", hidden)
            parameter("n", n)
            parameter("offset", offset)
        }.checkSuccess()
    }


    suspend fun acceptFriendRequest(notificationId: String): Result<VRChatResponse> = runCatching {
        client.put {
            url {
                path(
                    AUTH_API_PREFIX,
                    USER_API_PREFIX,
                    NOTIFICATIONS_API_PREFIX,
                    notificationId,
                    "accept"
                )
            }
        }.checkSuccess()
    }


    suspend fun markNotificationAsRead(notificationId: String): VRChatResponse =
        client.put {
            url {
                path(
                    AUTH_API_PREFIX,
                    USER_API_PREFIX,
                    NOTIFICATIONS_API_PREFIX,
                    notificationId,
                    "see"
                )
            }
        }.checkSuccess()

    suspend fun deleteNotification(notificationId: String): Result<VRChatResponse> = runCatching {
        client.put {
            url {
                path(
                    AUTH_API_PREFIX,
                    USER_API_PREFIX,
                    NOTIFICATIONS_API_PREFIX,
                    notificationId,
                    "hide"
                )
            }
        }.checkSuccess()
    }

    suspend fun clearAllNotifications(): VRChatResponse =
        client.put {
            url { path(AUTH_API_PREFIX, USER_API_PREFIX, NOTIFICATIONS_API_PREFIX, "clear") }
        }.checkSuccess()

}
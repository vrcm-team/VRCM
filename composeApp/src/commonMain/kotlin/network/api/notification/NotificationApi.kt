package io.github.vrcmteam.vrcm.network.api.notification

import io.github.vrcmteam.vrcm.network.api.attributes.AUTH_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.NOTIFICATIONS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.NotificationType
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.VRChatResponse
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationDataV2
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.github.vrcmteam.vrcm.presentation.screens.home.data.NotificationItemData
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.path

class NotificationApi(
    private val client: HttpClient,
) {

    suspend fun fetchNotifications(limit: Int = 100): List<NotificationData> =
        client.get(NOTIFICATIONS_API_PREFIX) {
            parameter("limit", limit)
        }.checkSuccess()


    suspend fun responseNotification(id: String, response: NotificationItemData.ActionData): String =
         client.post("$NOTIFICATIONS_API_PREFIX/$id/respond") {
            setBody(
                TextContent(
                    """{"responseData":"${response.data}","responseType":"${response.type}"}""",
                    ContentType.Application.Json
                )
            )
        }.checkSuccess{ bodyAsText() }


    suspend fun fetchNotificationsV2(
        type: String = NotificationType.All.value,
        // the only type you can see hidden content on is friendRequest
        hidden: Boolean = false,
        n: Int = 100,
        offset: Int = 0,
    ): Result<List<NotificationDataV2>> =
        client.get {
            url { path(AUTH_API_PREFIX, USER_API_PREFIX, NOTIFICATIONS_API_PREFIX) }
            parameter("type", type)
            parameter("hidden", hidden)
            parameter("n", n)
            parameter("offset", offset)
        }.checkSuccess()



    suspend fun acceptFriendRequest(notificationId: String): Result<VRChatResponse> =
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

    suspend fun deleteNotification(notificationId: String): Result<VRChatResponse> =
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


    suspend fun clearAllNotifications(): VRChatResponse =
        client.put {
            url { path(AUTH_API_PREFIX, USER_API_PREFIX, NOTIFICATIONS_API_PREFIX, "clear") }
        }.checkSuccess()

}
package io.github.vrcmteam.vrcm.network.api.notification

import io.github.vrcmteam.vrcm.network.api.attributes.NOTIFICATIONS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import network.api.notification.data.ResponseData

class NotificationApi(
    private val client: HttpClient,
) {

    suspend fun fetchNotifications(limit: Int = 100 ): Result<List<NotificationData>> {
        return client.get(NOTIFICATIONS_API_PREFIX) {
            parameter("limit", limit)
        }.ifOK { body() }
    }

    suspend fun responseNotification(id: String, response: ResponseData): Result<String> {
        return client.post("$NOTIFICATIONS_API_PREFIX/$id/respond"){
            setBody(TextContent("""{"responseData":"${response.responseData}","type":"${response.type}""", ContentType.Application.Json))
        }.ifOK { bodyAsText() }
    }
}
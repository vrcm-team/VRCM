package io.github.vrcmteam.vrcm.network.api.notification

import io.github.vrcmteam.vrcm.network.api.attributes.NOTIFICATIONS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.github.vrcmteam.vrcm.network.extensions.ifOK
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
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
            setBody(TextContent("""{"responseData":"${response.responseData}","responseType":"${response.type}"}""", ContentType.Application.Json))
        }.ifOK { bodyAsText() }
    }
}
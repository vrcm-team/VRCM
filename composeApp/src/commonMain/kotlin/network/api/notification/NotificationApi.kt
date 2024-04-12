package io.github.vrcmteam.vrcm.network.api.notification

import io.github.vrcmteam.vrcm.network.api.attributes.NOTIFICATIONS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.notification.data.NotificationData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class NotificationApi(
    private val client: HttpClient,
) {

    suspend fun fetchNotifications(limit: Int = 100 ): List<NotificationData> {
        return client.get(NOTIFICATIONS_API_PREFIX) {
            parameter("limit", limit)
        }.body()
    }
}
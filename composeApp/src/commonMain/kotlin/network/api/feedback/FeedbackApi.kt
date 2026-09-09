package io.github.vrcmteam.vrcm.network.api.feedback

import io.github.vrcmteam.vrcm.network.api.attributes.FEEDBACK_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.USER_API_PREFIX
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.serialization.Serializable

class FeedbackApi(private val client: HttpClient) {
    suspend fun reportUser(userId: String) {
        client.post {
            url { path(FEEDBACK_API_PREFIX, userId, USER_API_PREFIX) }
            contentType(ContentType.Application.Json)
            setBody(
                UserReportRequest(
                    contentType = "user",
                    reason = "behavior-hacking",
                    type = "report",
                ),
            )
        }.checkSuccess { Unit }
    }
}

@Serializable
private data class UserReportRequest(
    val contentType: String,
    val reason: String,
    val type: String,
)

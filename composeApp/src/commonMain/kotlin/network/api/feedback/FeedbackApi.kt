package io.github.vrcmteam.vrcm.network.api.feedback

import io.github.vrcmteam.vrcm.network.api.attributes.CONFIG_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.attributes.MODERATION_REPORTS_API_PREFIX
import io.github.vrcmteam.vrcm.network.api.feedback.data.ModerationReportRequest
import io.github.vrcmteam.vrcm.network.api.feedback.data.ReportConfigData
import io.github.vrcmteam.vrcm.network.extensions.checkSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class FeedbackApi(private val client: HttpClient) {
    /** 举报可选的类型、分类与原因，来自 VRChat 的公开配置。 */
    suspend fun reportConfig(): ReportConfigData =
        client.get(CONFIG_API_PREFIX).checkSuccess()

    /** 提交举报，由 VRChat 审核团队处理。 */
    suspend fun submitReport(request: ModerationReportRequest) {
        client.post(MODERATION_REPORTS_API_PREFIX) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.checkSuccess { Unit }
    }
}

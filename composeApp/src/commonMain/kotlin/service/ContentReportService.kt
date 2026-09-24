package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.feedback.FeedbackApi
import io.github.vrcmteam.vrcm.network.api.feedback.data.ModerationReportRequest
import io.github.vrcmteam.vrcm.network.api.feedback.data.ReportConfigData
import io.github.vrcmteam.vrcm.service.data.ContentReportType
import io.github.vrcmteam.vrcm.service.data.ReportCategoryOption
import io.github.vrcmteam.vrcm.service.data.ReportTarget
import io.github.vrcmteam.vrcm.service.data.categoriesFor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 举报用户、世界、模型与群组：可选分类与原因来自 VRChat 的 `/config`（与账号无关，进程内缓存一份），
 * 举报提交给 VRChat 审核团队处理。
 */
class ContentReportService(
    private val feedbackApi: FeedbackApi,
    private val authService: AuthService,
) {
    private val configMutex = Mutex()
    private var reportConfig: ReportConfigData? = null

    suspend fun reportCategories(type: ContentReportType): Result<List<ReportCategoryOption>> = runCatching {
        configMutex.withLock {
            reportConfig ?: feedbackApi.reportConfig().also { reportConfig = it }
        }.categoriesFor(type)
    }

    suspend fun submit(
        target: ReportTarget,
        category: String,
        reason: String,
        description: String,
    ): Result<Unit> = authService.reTryAuthCatching {
        feedbackApi.submitReport(
            ModerationReportRequest(
                type = target.type.apiValue,
                category = category,
                reason = reason,
                contentId = target.contentId,
                description = description.trim().takeIf { it.isNotEmpty() },
            )
        )
    }
}

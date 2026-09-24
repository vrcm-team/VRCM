package io.github.vrcmteam.vrcm.network.api.feedback.data

import kotlinx.serialization.Serializable

/**
 * `GET /config` 里与举报有关的三项：
 * [reportOptions] 是 类型 → 分类 → 原因列表；[reportCategories]、[reportReasons] 是分类与原因的展示文案。
 */
@Serializable
data class ReportConfigData(
    val reportCategories: Map<String, ReportCategoryData> = emptyMap(),
    val reportOptions: Map<String, Map<String, List<String>>> = emptyMap(),
    val reportReasons: Map<String, ReportReasonData> = emptyMap(),
)

@Serializable
data class ReportCategoryData(
    val text: String = "",
    val tooltip: String = "",
    val order: Int? = null,
)

@Serializable
data class ReportReasonData(
    val text: String = "",
    val tooltip: String = "",
)

/** `POST /moderationReports` 请求体；[category]、[reason] 必须是 `/config` 中 [type] 下的有效组合。 */
@Serializable
data class ModerationReportRequest(
    val type: String,
    val category: String,
    val reason: String,
    val contentId: String,
    val description: String? = null,
)

package io.github.vrcmteam.vrcm.service.data

import io.github.vrcmteam.vrcm.network.api.feedback.data.ReportConfigData

/** 可举报的内容类型，[apiValue] 对应 `/config` 中 reportOptions 的键与举报请求的 type。 */
enum class ContentReportType(val apiValue: String) {
    User("user"),
    World("world"),
    Avatar("avatar"),
    Group("group"),
}

/** 被举报的对象：[displayName] 只用于在举报面板里提示用户正在举报谁。 */
data class ReportTarget(
    val type: ContentReportType,
    val contentId: String,
    val displayName: String,
)

/** 一个可选的举报分类；[label] 是 VRChat 返回的英文文案，界面有本地化文案时优先用本地化文案。 */
data class ReportCategoryOption(
    val key: String,
    val label: String,
    val reasons: List<ReportReasonOption>,
)

data class ReportReasonOption(
    val key: String,
    val label: String,
)

/** 需要额外附带内容标签（details.suggestedWarnings）才能提交的原因，举报面板不提供。 */
private val ReasonsRequiringDetails = setOf("warnings")

/**
 * 取出某类内容可举报的分类与原因，按 VRChat 给的 order 排序。
 * 商店账单类分类（`*store`）不是内容举报；没有展示文案或需要额外资料的原因不提供；没有可选原因的分类整组略过。
 */
fun ReportConfigData.categoriesFor(type: ContentReportType): List<ReportCategoryOption> =
    reportOptions[type.apiValue].orEmpty()
        .filterKeys { !it.endsWith("store") }
        .mapNotNull { (categoryKey, reasonKeys) ->
            val reasons = reasonKeys
                .filterNot { it in ReasonsRequiringDetails }
                .mapNotNull { reasonKey ->
                    reportReasons[reasonKey]?.text?.takeIf { it.isNotBlank() }
                        ?.let { ReportReasonOption(key = reasonKey, label = it) }
                }
            if (reasons.isEmpty()) return@mapNotNull null
            val category = reportCategories[categoryKey]
            val option = ReportCategoryOption(
                key = categoryKey,
                label = category?.text?.takeIf { it.isNotBlank() } ?: categoryKey,
                reasons = reasons,
            )
            (category?.order ?: Int.MAX_VALUE) to option
        }
        .sortedBy { (order, _) -> order }
        .map { (_, option) -> option }

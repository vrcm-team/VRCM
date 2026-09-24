package io.github.vrcmteam.vrcm.service

import io.github.vrcmteam.vrcm.network.api.feedback.data.ReportCategoryData
import io.github.vrcmteam.vrcm.network.api.feedback.data.ReportConfigData
import io.github.vrcmteam.vrcm.network.api.feedback.data.ReportReasonData
import io.github.vrcmteam.vrcm.service.data.ContentReportType
import io.github.vrcmteam.vrcm.service.data.categoriesFor
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentReportOptionsTest {
    @Test
    fun onlySubmittableContentCategoriesAreOfferedInVrchatOrder() {
        val config = ReportConfigData(
            reportOptions = mapOf(
                "world" to mapOf(
                    "worldstore" to listOf("billing"),
                    "worldpage" to listOf("sexual", "warnings", "unlabeled"),
                    "worldui" to listOf("sexual"),
                    "worldaudio" to listOf("unlabeled"),
                    "worldobject" to listOf("other"),
                ),
                "user" to mapOf("behavior" to listOf("hacking")),
            ),
            reportCategories = mapOf(
                "worldpage" to ReportCategoryData(text = "World Page", order = 16),
                "worldui" to ReportCategoryData(text = "UI/FX of World", order = 15),
                "worldstore" to ReportCategoryData(text = "World Store", order = 17),
                "worldaudio" to ReportCategoryData(text = "Audio in World", order = 14),
            ),
            reportReasons = mapOf(
                "sexual" to ReportReasonData(text = "Sexual Content"),
                "billing" to ReportReasonData(text = "Billing"),
                "warnings" to ReportReasonData(text = "Missing content warnings"),
                "other" to ReportReasonData(text = "Something else"),
                "hacking" to ReportReasonData(text = "Client Modding / Hacking"),
            ),
        )

        val categories = config.categoriesFor(ContentReportType.World)

        // 商店分类不是内容举报；没有可提交原因的分类整组略过；缺 order 的排在最后
        assertEquals(listOf("worldui", "worldpage", "worldobject"), categories.map { it.key })
        // warnings 需要额外的内容标签、unlabeled 没有展示文案，都不提供
        assertEquals(listOf("sexual"), categories[1].reasons.map { it.key })
        // 没有分类文案时用键兜底
        assertEquals("worldobject", categories[2].label)
    }
}
